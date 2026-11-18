package dev.xiaoman.hands

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureScreenPolicyTest {
    @Test
    fun firstUnreadableDoesNotConfirm() {
        val confirmation = SecureConfirmation(required = 2)
        assertFalse(confirmation.onUnreadable())
    }

    @Test
    fun secondConsecutiveUnreadableConfirms() {
        val confirmation = SecureConfirmation(required = 2)
        assertFalse(confirmation.onUnreadable())
        assertTrue(confirmation.onUnreadable())
    }

    @Test
    fun readableFrameResetsConsecutiveCount() {
        val confirmation = SecureConfirmation(required = 2)
        assertFalse(confirmation.onUnreadable())
        confirmation.onReadable()
        assertFalse(confirmation.onUnreadable())
        assertTrue(confirmation.onUnreadable())
    }

    @Test
    fun secureWindowIsAlwaysTrusted() {
        assertTrue(
            SecureScreenPolicy.keepSecure(
                SecureReason.SECURE_WINDOW,
                nodeCount = 10,
                packageName = "com.sankuai.meituan",
            ),
        )
    }

    @Test
    fun allBlackWithNodesFromNormalAppUsesNodes() {
        assertFalse(
            SecureScreenPolicy.keepSecure(
                SecureReason.ALL_BLACK,
                nodeCount = 10,
                packageName = "com.sankuai.meituan",
            ),
        )
    }

    @Test
    fun allBlackWithoutNodesStaysSecure() {
        assertTrue(
            SecureScreenPolicy.keepSecure(
                SecureReason.ALL_BLACK,
                nodeCount = 0,
                packageName = "com.sankuai.meituan",
            ),
        )
    }

    @Test
    fun allBlackFromSystemDialogStaysSecure() {
        assertTrue(
            SecureScreenPolicy.keepSecure(
                SecureReason.ALL_BLACK,
                nodeCount = 10,
                packageName = "com.miui.securitycenter",
            ),
        )
    }

    @Test
    fun normalAppClassification() {
        assertTrue(SecureScreenPolicy.isNormalApp("com.sankuai.meituan"))
        assertFalse(SecureScreenPolicy.isNormalApp("com.android.systemui"))
        assertFalse(SecureScreenPolicy.isNormalApp("com.android.permissioncontroller"))
        assertFalse(SecureScreenPolicy.isNormalApp(null))
    }
}
