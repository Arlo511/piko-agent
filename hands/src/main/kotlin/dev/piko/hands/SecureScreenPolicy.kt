package dev.piko.hands

/** Why a screenshot could not be read. */
internal enum class SecureReason {
    /** The system explicitly reported ERROR_TAKE_SCREENSHOT_SECURE_WINDOW. */
    SECURE_WINDOW,

    /** The captured frame is entirely black (Android 11-13 reports FLAG_SECURE this way). */
    ALL_BLACK,
}

/**
 * Counts consecutive unreadable captures. A readable frame resets the count, so a single
 * transition frame does not pause the task.
 */
internal class SecureConfirmation(private val required: Int = 2) {
    private var consecutive = 0

    fun onReadable() {
        consecutive = 0
    }

    fun onUnreadable(): Boolean {
        consecutive++
        return consecutive >= required
    }
}

internal object SecureScreenPolicy {
    /** System UI, settings and permission/security dialogs are not "normal apps". */
    private val systemPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.permissionmanager",
        "com.miui.packageinstaller",
        "com.lbe.security.miui",
    )

    fun isNormalApp(packageName: String?): Boolean =
        packageName != null &&
            packageName !in systemPackages &&
            !packageName.contains("permissioncontroller", ignoreCase = true)

    /**
     * A system-reported secure window is always trusted. An all-black frame is only trusted when
     * there is no readable node tree from a normal app; otherwise it is most likely a transition
     * frame, and the node tree is enough for the operator to continue.
     */
    fun keepSecure(reason: SecureReason, nodeCount: Int, packageName: String?): Boolean = when (reason) {
        SecureReason.SECURE_WINDOW -> true
        SecureReason.ALL_BLACK -> !(nodeCount > 0 && isNormalApp(packageName))
    }
}
