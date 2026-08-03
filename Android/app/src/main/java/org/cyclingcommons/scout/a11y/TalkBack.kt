package org.cyclingcommons.scout.a11y

import android.content.Context
import android.view.accessibility.AccessibilityManager

/** True when TalkBack (or another touch-exploration screen reader) is active. */
fun Context.isTalkBackActive(): Boolean {
    val manager = getSystemService(AccessibilityManager::class.java) ?: return false
    return manager.isEnabled && manager.isTouchExplorationEnabled
}
