package com.example.friendminder.ui.friendlist

import com.example.friendminder.data.models.Contact

/**
 * One row in the FriendListFragment picker. [isMissing] flags a saved friend
 * whose contact no longer resolves on the device (Designer spec §4.4) — such
 * rows have no checkbox and are rendered greyed-out with a "Not found" label.
 */
data class PickerItem(
    val contact: Contact,
    val isSelected: Boolean,
    val isMissing: Boolean
)
