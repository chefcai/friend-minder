package com.example.friendminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.example.friendminder.databinding.ActivityMainBinding
import com.example.friendminder.ui.groups.GroupDetailFragment
import com.example.friendminder.ui.groups.GroupsFragment
import com.example.friendminder.ui.history.OverallHistoryFragment
import com.example.friendminder.ui.home.HomeFragment
import com.example.friendminder.ui.home.LegacyDiagnosticsFragment
import com.example.friendminder.ui.settings.AdvancedSettingsFragment
import com.example.friendminder.ui.settings.SettingsFragment

/**
 * Navigation shell (FRM-100, SCREENS-PHASE3.md §2): hosts every screen via
 * plain FragmentTransactions - no Navigation-Component dependency,
 * consistent with the project's minimal, easy-to-audit dependency tree
 * (see ServiceLocator).
 *
 * FRM-102 (§9.0, "onboarding is dropped"): this used to branch its start
 * destination on "do you have friends yet" - zero contacts launched
 * straight into FriendListFragment's onboarding mode, which meant Home's
 * FRM-107 empty state could never actually be reached. The app now always
 * launches to Home; zero contacts is just Home's empty state, and "Add
 * someone" is how you get your first ones tracked.
 *
 * Also owns the bottom nav (FRM-100, revised on GH #117's own follow-up
 * discussion, Cai 2026-09-21): exactly three icon-only destinations
 * (Groups, Overall History, Settings), now permanently visible on every
 * screen in the app rather than only the four original Phase 3 "shell"
 * screens - Contact Detail, Group Detail, Advanced Settings, Notifications
 * & Diagnostics, and the add-contacts flow's two steps all show it too, so
 * you can always jump to a top-level destination without backing all the
 * way out first. [updateBottomNav] is the single source of truth for the
 * bar's selected item (never its visibility anymore - see below), driven
 * off whatever fragment [R.id.nav_host_container] currently holds - re-run
 * on every back-stack change (a fragment-manager listener, registered
 * once) and, for the one case that isn't a back-stack change, the very
 * first root commit. A screen with an unambiguous single parent section -
 * Advanced Settings, Notifications & Diagnostics (both only ever pushed
 * from Settings), Group Detail (only ever pushed from Groups) - shows that
 * parent's icon selected too (FRM-135), so the bar still answers "which
 * section am I in" one level deep. Home and every screen that isn't itself
 * one of the three real destinations or one of those three unambiguous
 * children shows no item selected. Contact Detail is deliberately excluded
 * from that FRM-135 mapping even though it's a child screen: it's reached
 * from both Home and Group Detail, so which bottom-nav item (if any) it
 * should light up depends on how you got there, not just which fragment
 * class is on screen - [R.id.nav_host_container] alone can't answer that,
 * and neither this fix nor GH #117's original back-stack note attempt to
 * add entry-point tracking to resolve it.
 *
 * Back-stack shape (SCREENS-PHASE3.md §2.3): switching between nav
 * destinations *replaces* rather than stacks - [navigateToDestination] pops
 * any existing [NAV_DESTINATION_BACK_STACK_NAME] entry before pushing the
 * new one, so the stack never grows past Home -> {destination} ->
 * {detail...}. A detail screen opened from within a destination (Contact
 * Detail, Group Detail, Advanced Settings) still uses a plain, untagged
 * [FragmentManager.commit] with [androidx.fragment.app.FragmentTransaction.addToBackStack]
 * (unchanged in GroupsFragment/SettingsFragment/etc.), so popping it only
 * removes that one entry and returns to the destination underneath. Note
 * this means a nav-bar tap taken from one of those untagged detail screens
 * doesn't collapse the stack the way one taken from a shell screen does -
 * back from the newly-opened destination returns to the detail screen you
 * tapped from, not all the way to Home - since [navigateToDestination] only
 * pops entries carrying [NAV_DESTINATION_BACK_STACK_NAME]. Not fixed here;
 * flagged as a known consequence of making the bar universally tappable
 * rather than a full back-stack redesign.
 *
 * FRM-78: also handles [ACTION_OPEN_SETTINGS], the tap action for the test
 * notification (see NotificationHelper.postTestNotification) - the only
 * external entry point into this activity today besides plain launch.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // FRM-65: must run before super.onCreate() per the SplashScreen API contract.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Single global back handler: pop the fragment back stack while
        // there's something on it (Designer spec §2.1/§2.2 "edit" mode back
        // arrows behave the same as system back); otherwise fall through to
        // the platform default, which finishes the activity.
        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setUpBottomNav()
        supportFragmentManager.addOnBackStackChangedListener { updateBottomNav() }

        if (savedInstanceState == null) {
            if (intent?.action == ACTION_OPEN_SETTINGS) {
                openSettings()
            } else {
                // commitNow (not commit): commit() posts the transaction to the
                // main-thread message queue instead of running it
                // immediately, so the updateBottomNav() call right below was
                // observing the fragment host before the replace() had
                // actually landed (findFragmentById still null) and hiding
                // the bar on every cold start. commitNow() is exactly the
                // escape hatch for that, and is legal here only because this
                // transaction never uses addToBackStack (it's the
                // back-stack base) - commitNow() throws if you try to
                // combine the two.
                supportFragmentManager.commitNow {
                    replace(R.id.nav_host_container, HomeFragment.newInstance())
                }
                // The commit above has no back-stack entry, so it never
                // fires addOnBackStackChangedListener - the only place that
                // needs a manual call.
                updateBottomNav()
            }
        } else {
            updateBottomNav()
        }
    }

    // FRM-78: reached when the activity is already running and the test
    // notification's tap PendingIntent (FLAG_ACTIVITY_SINGLE_TOP) is
    // delivered to the existing instance instead of creating a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_SETTINGS) {
            openSettings()
        }
    }

    private fun openSettings() {
        navigateToDestination(SettingsFragment.newInstance())
    }

    private fun setUpBottomNav() {
        applyBottomNavInsets()

        // DESIGN-SYSTEM-PHASE3.md §6: "No Material 3 active-indicator pill."
        // Not available as an XML attribute in this project's Material
        // Components version (1.14.0 rejects itemActiveIndicatorEnabled in
        // layout XML - AAPT2 link failure), so it's set programmatically.
        binding.bottomNav.setItemActiveIndicatorEnabled(false)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val current = supportFragmentManager.findFragmentById(R.id.nav_host_container)
            val destination = when (item.itemId) {
                R.id.nav_groups -> if (current is GroupsFragment) null else GroupsFragment.newInstance()
                R.id.nav_overall_history -> if (current is OverallHistoryFragment) null else OverallHistoryFragment.newInstance()
                R.id.nav_settings -> if (current is SettingsFragment) null else SettingsFragment.newInstance()
                else -> null
            }
            destination?.let { navigateToDestination(it) }
            true
        }

        // DESIGN-SYSTEM-PHASE3.md §8: "the three bottom-nav items
        // additionally have tooltipText for long-press, since they carry no
        // labels." The menu's android:title already gives each item its
        // accessibility label; this adds the explicit long-press tooltip on
        // top of that.
        listOf(
            R.id.nav_groups to R.string.nav_label_groups,
            R.id.nav_overall_history to R.string.nav_label_overall_history,
            R.id.nav_settings to R.string.nav_label_settings
        ).forEach { (itemId, labelRes) ->
            binding.bottomNav.findViewById<android.view.View>(itemId)?.let {
                TooltipCompat.setTooltipText(it, getString(labelRes))
            }
        }
    }

    /**
     * FRM-115: the bar's items must stay within a real, un-eclipsed
     * @dimen/fm_bottom_nav_height (64dp) even on a gesture-nav device,
     * where the system's own nav-bar/gesture-pill chrome is drawn over
     * the bottom of whatever's there - previously nothing accounted for
     * that inset at all, so it silently ate into the bar's fixed height
     * instead of being added on top of it, and left the items visually
     * cramped against the top divider. Mirrors HomeFragment's
     * statusBarSpacer idiom for the header's top inset: grow the view by
     * the bottom inset and give the extra space back as bottom padding,
     * so the fill extends behind the gesture area while the items
     * themselves are laid out in the untouched top 64dp. A zero inset
     * (non-gesture-nav devices, or a screen where the bar's insets are
     * already consumed higher up) is a no-op - height and padding both
     * collapse back to exactly what the XML declared.
     */
    private fun applyBottomNavInsets() {
        val barContentHeight = binding.bottomNav.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottomInset)
            view.layoutParams = view.layoutParams.apply {
                height = barContentHeight + bottomInset
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.bottomNav)
    }

    /**
     * Pops any existing nav-destination entry (so switching between Groups /
     * Overall History / Settings replaces rather than stacks - §2.3), then
     * pushes [fragment] as the new one. Not used for Home itself: Home is
     * the back-stack base, added once in [onCreate] without a name.
     */
    private fun navigateToDestination(fragment: Fragment) {
        supportFragmentManager.popBackStackImmediate(
            NAV_DESTINATION_BACK_STACK_NAME,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        supportFragmentManager.commit {
            replace(R.id.nav_host_container, fragment)
            addToBackStack(NAV_DESTINATION_BACK_STACK_NAME)
        }
    }

    /**
     * Single source of truth for the bottom nav's selected item
     * (SCREENS-PHASE3.md §2.1/§2.2). Cai's 2026-09-21 follow-up to GH #117
     * made the bar permanently visible on every screen - see this class's
     * own kdoc - so this function no longer touches visibility at all,
     * only which item (if any) shows selected, driven off whichever
     * fragment [R.id.nav_host_container] currently holds. Home and every
     * screen that isn't itself Groups/Overall History/Settings (Contact
     * Detail, Group Detail, Advanced Settings, Notifications &
     * Diagnostics, both add-contacts steps) fall through to
     * [HOME_NO_SELECTION] - the name predates this generalization but the
     * behavior (no item selected) is exactly what all of them want.
     */
    private fun updateBottomNav() {
        val current = supportFragmentManager.findFragmentById(R.id.nav_host_container)
        val destinationItemId = when (current) {
            is GroupsFragment -> R.id.nav_groups
            is OverallHistoryFragment -> R.id.nav_overall_history
            is SettingsFragment -> R.id.nav_settings
            // FRM-135: a sub-menu screen with exactly one possible parent
            // section shows that parent's icon selected/active, so the bar
            // still answers "which section am I in" for these one-level-
            // deep screens instead of going dark. Covers the two Settings
            // children plus Group Detail; Contact Detail is deliberately
            // excluded (see class kdoc) since it has no single unambiguous
            // parent.
            is AdvancedSettingsFragment -> R.id.nav_settings
            is LegacyDiagnosticsFragment -> R.id.nav_settings
            is GroupDetailFragment -> R.id.nav_groups
            else -> HOME_NO_SELECTION
        }

        // §2.2: "On Home, all three glyphs sit inactive" - BottomNavigationView
        // otherwise always keeps exactly one item checked, so this is the one
        // place that needs setGroupCheckable(false) rather than just
        // selecting an item. Menu items not declared inside a <group> tag
        // belong to the implicit group id 0 (see menu_bottom_nav.xml).
        //
        // Confirmed on-device (uiautomator dump of a cold-started Home
        // screen): setGroupCheckable(0, false, true) alone already yields
        // isChecked=false on every item, which is what our icon selectors
        // (ic_nav_*.xml, keyed on android:state_checked) actually key off -
        // so all three glyphs do render outline/inactive as designed. The
        // explicit un-check loop below is belt-and-suspenders for that part
        // and costs nothing.
        //
        // What it does NOT clear: BottomNavigationView still reports
        // Groups (the first declared item) as View.isSelected()==true
        // internally, left over from auto-selecting the first item at menu
        // inflation, before this function ever runs - confirmed via the
        // same dump (selected="true" on nav_groups, checked="false" on all
        // three). That's a distinct flag from isChecked and doesn't affect
        // the checked-state icon drawables, but it does mean TalkBack would
        // announce Groups as "selected" while sitting on Home with nothing
        // actually selected. GH #110: fixed below via
        // syncBottomNavSelectedState() rather than reaching into
        // NavigationBarMenuView's private selection tracking (mSelectedItemId)
        // - that field drives nothing user-visible on its own, but
        // View.isSelected() on each item's real ItemView is exactly what
        // TalkBack reads, so setting it directly is both simpler and more
        // robust than fighting the library's internals.
        if (destinationItemId == HOME_NO_SELECTION) {
            val menu = binding.bottomNav.menu
            menu.setGroupCheckable(0, false, true)
            for (i in 0 until menu.size()) {
                menu.getItem(i).isChecked = false
            }
        } else {
            binding.bottomNav.menu.setGroupCheckable(0, true, true)
            binding.bottomNav.menu.findItem(destinationItemId).isChecked = true
        }
        syncBottomNavSelectedState(destinationItemId)
    }

    // GH #110: BottomNavigationView/NavigationBarMenuView auto-selects the
    // first declared item (Groups) at menu inflation time - before
    // updateBottomNav() ever runs - and that leaves its real ItemView's
    // View.isSelected() stuck at true even after menu.getItem(i).isChecked
    // = false above successfully clears the checked-state icon drawables.
    // isChecked and isSelected are tracked separately by the library;
    // clearing the former doesn't clear the latter. Confirmed via
    // uiautomator dump: nav_groups reported selected="true" (cascading down
    // through every descendant view - content/icon/inner-content
    // containers, the icon ImageView itself) while sitting on Home with
    // checked="false" on all three items. Explicitly setting each item's
    // real ItemView.isSelected here - rather than the MenuItem's isChecked
    // - fixes exactly what TalkBack/AccessibilityNodeInfo.isSelected()
    // actually reads, and cascades correctly to every descendant since
    // ViewGroup.setSelected() dispatches to children by default.
    private fun syncBottomNavSelectedState(destinationItemId: Int?) {
        listOf(R.id.nav_groups, R.id.nav_overall_history, R.id.nav_settings).forEach { itemId ->
            binding.bottomNav.findViewById<android.view.View>(itemId)?.isSelected = itemId == destinationItemId
        }
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.example.friendminder.action.OPEN_SETTINGS"
        private const val NAV_DESTINATION_BACK_STACK_NAME = "nav_destination"
        private const val HOME_NO_SELECTION = 0
    }
}
