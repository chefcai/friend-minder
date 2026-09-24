package com.example.friendminder.ui.settings

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.storage.SlotScheduling
import com.example.friendminder.databinding.SectionReminderTimeBinding
import com.example.friendminder.ui.common.stackIfLabelsDontFit
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * FRM-178 (Cai, option B, 2026-09-24): the REMINDER TIME section, moved from
 * Settings to the Notifications screen to bring Settings under the audit's
 * decision limit. Everything here is the behaviour Settings had (FRM-130
 * segmented mode, FRM-166 single Time row, FRM-164 status line) - it just
 * owns its own state and saves on its own now.
 *
 * Because the time and the reminders-per-day count now live on different
 * screens, each screen saves its own values and then calls [reschedule],
 * which reads the *stored* time and count so neither screen can schedule
 * with a stale copy of the other's value.
 */
class ReminderTimeController(
    private val fragment: Fragment,
    private val binding: SectionReminderTimeBinding,
    private val onSaved: () -> Unit = {}
) {
    private val context: Context get() = fragment.requireContext()

    private var fixedHour = DEFAULT_FIXED_HOUR
    private var fixedMinute = 0
    private var randomStartHour = DEFAULT_RANDOM_START_HOUR
    private var randomEndHour = DEFAULT_RANDOM_END_HOUR
    private var isRandomTimeMode = false
    private var isLoading = false

    fun start() {
        bindControls()
        load()
    }

    private fun bindControls() {
        fun selectTimeMode(isRandom: Boolean) {
            isRandomTimeMode = isRandom
            binding.fixedTimeToggleButton.isChecked = !isRandom
            binding.randomWindowToggleButton.isChecked = isRandom
            changed()
        }
        binding.timeModeGroup.stackIfLabelsDontFit()
        binding.fixedTimeToggleButton.setOnClickListener { selectTimeMode(isRandom = false) }
        binding.randomWindowToggleButton.setOnClickListener { selectTimeMode(isRandom = true) }

        // FRM-166 (ST-4): fixed time opens the time picker; the random window
        // opens the start-hour picker and then the end-hour picker (whole
        // hours only - the random scheduler only takes whole hours).
        binding.timeRow.setOnClickListener {
            if (!isRandomTimeMode) {
                TimePickerDialog(context, { _, hour, minute ->
                    fixedHour = hour; fixedMinute = minute; changed()
                }, fixedHour, fixedMinute, false).show()
                return@setOnClickListener
            }
            TimePickerDialog(context, { _, startHour, _ ->
                randomStartHour = startHour; changed()
                TimePickerDialog(context, { _, endHour, _ ->
                    randomEndHour = endHour; changed()
                }, randomEndHour, 0, false).apply { setTitle(R.string.label_to) }.show()
            }, randomStartHour, 0, false).apply { setTitle(R.string.label_from) }.show()
        }
    }

    private fun changed() {
        render()
        if (validate() && !isLoading) save()
    }

    private fun load() {
        isLoading = true
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val repo = ServiceLocator.settingsRepository
            isRandomTimeMode = repo.isRandomTimeEnabled()
            binding.fixedTimeToggleButton.isChecked = !isRandomTimeMode
            binding.randomWindowToggleButton.isChecked = isRandomTimeMode
            repo.getReminderTime()?.let { (h, m) -> fixedHour = h; fixedMinute = m }
            repo.getRandomTimeRange()?.let { (s, e) -> randomStartHour = s; randomEndHour = e }
            render()
            validate()
            isLoading = false
        }
    }

    private fun save() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val repo = ServiceLocator.settingsRepository
            repo.setRandomTimeEnabled(isRandomTimeMode)
            repo.setReminderTime(fixedHour, fixedMinute)
            repo.setRandomTimeRange(randomStartHour, randomEndHour)
            reschedule()
            onSaved()
        }
    }

    private fun render() {
        if (isRandomTimeMode) {
            binding.timeLabel.setText(R.string.label_time_window)
            binding.timeValue.text = context.getString(
                R.string.format_time_window,
                formatTimeLabel(randomStartHour, 0),
                formatTimeLabel(randomEndHour, 0)
            )
        } else {
            binding.timeLabel.setText(R.string.label_time)
            binding.timeValue.text = formatTimeLabel(fixedHour, fixedMinute)
        }
    }

    // GH #150 follow-up: which slot (today vs tomorrow) the entered time
    // produces. Random mode uses the window's end hour as the threshold -
    // validate() guarantees end > start, so there is no midnight wrap.
    private val nextReminderNoticeText = {
        val now = System.currentTimeMillis()
        if (isRandomTimeMode) {
            val isToday = SlotScheduling.occursLaterToday(randomEndHour, minute = 0, nowMillis = now)
            context.getString(
                if (isToday) R.string.notice_next_reminder_today_random else R.string.notice_next_reminder_tomorrow_random,
                formatTimeLabel(randomStartHour),
                formatTimeLabel(randomEndHour)
            )
        } else {
            val isToday = SlotScheduling.occursLaterToday(fixedHour, fixedMinute, nowMillis = now)
            context.getString(
                if (isToday) R.string.notice_next_reminder_today_fixed else R.string.notice_next_reminder_tomorrow_fixed,
                formatTimeLabel(fixedHour, fixedMinute)
            )
        }
    }

    // FRM-164 (ST-1): one status line. The notice is fm_ink_dim with an
    // inline 16dp clock icon (an ImageSpan, so it stays on the first line
    // when the text wraps); an end hour at or before the start hour is the
    // one state the user must fix, shown in fm_error with no icon.
    private fun validate(): Boolean {
        val valid = !isRandomTimeMode || randomEndHour > randomStartHour
        val status = binding.timeStatusText
        status.setTextColor(ContextCompat.getColor(context, if (valid) R.color.fm_ink_dim else R.color.fm_error))
        status.text = if (valid) {
            val res = context.resources
            val size = res.getDimensionPixelSize(R.dimen.fm_status_icon_size)
            val gap = res.getDimensionPixelSize(R.dimen.fm_status_icon_gap)
            val icon = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_schedule_24)).mutate()
            icon.setTint(ContextCompat.getColor(context, R.color.fm_ink_dim))
            val inset = InsetDrawable(icon, 0, 0, gap, 0).apply { setBounds(0, 0, size + gap, size) }
            val align = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DynamicDrawableSpan.ALIGN_CENTER
            } else {
                DynamicDrawableSpan.ALIGN_BASELINE
            }
            SpannableStringBuilder(" ").apply {
                setSpan(ImageSpan(inset, align), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(nextReminderNoticeText())
            }
        } else {
            context.getString(R.string.error_end_before_start)
        }
        return valid
    }

    companion object {
        private const val DEFAULT_FIXED_HOUR = 8
        private const val DEFAULT_RANDOM_START_HOUR = 7
        private const val DEFAULT_RANDOM_END_HOUR = 9

        /** "5:00 AM" with a minute, "5 AM" without (the random window's whole hours). */
        fun formatTimeLabel(hour: Int, minute: Int? = null): String {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute ?: 0)
            }
            return SimpleDateFormat(if (minute != null) "h:mm a" else "h a", Locale.getDefault()).format(cal.time)
        }

        /** The stored reminder time as one value, for Settings' Notifications row. */
        suspend fun summary(context: Context): String {
            val repo = ServiceLocator.settingsRepository
            return if (repo.isRandomTimeEnabled()) {
                val (s, e) = repo.getRandomTimeRange() ?: (DEFAULT_RANDOM_START_HOUR to DEFAULT_RANDOM_END_HOUR)
                context.getString(R.string.format_time_window, formatTimeLabel(s, 0), formatTimeLabel(e, 0))
            } else {
                val (h, m) = repo.getReminderTime() ?: (DEFAULT_FIXED_HOUR to 0)
                formatTimeLabel(h, m)
            }
        }

        /**
         * Re-enqueue the daily reminders from the stored settings. Called by
         * both screens after they save, so the time (Notifications) and the
         * per-day count (Settings) are always taken from storage together.
         */
        suspend fun reschedule() {
            val repo = ServiceLocator.settingsRepository
            val perDay = repo.getContactsPerDay()
            val scheduler = ServiceLocator.notificationScheduler
            if (repo.isRandomTimeEnabled()) {
                val (s, e) = repo.getRandomTimeRange() ?: (DEFAULT_RANDOM_START_HOUR to DEFAULT_RANDOM_END_HOUR)
                scheduler.scheduleWithRandomTime(s, e, perDay)
            } else {
                val (h, m) = repo.getReminderTime() ?: (DEFAULT_FIXED_HOUR to 0)
                scheduler.scheduleDaily(h, m, perDay)
            }
        }
    }
}
