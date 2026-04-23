package io.heckel.ntfy.msg

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BroadcastReceiverSecurityTest {
    @Test
    fun sendMessageReceiver_requiresSignaturePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val receiverClassName = "io.heckel.ntfy.msg.BroadcastService\$BroadcastReceiver"
        val componentName = ComponentName(context.packageName, receiverClassName)
        @Suppress("DEPRECATION")
        val receiverInfo = packageManager.getReceiverInfo(componentName, 0)

        assertTrue("SEND_MESSAGE receiver must remain exported", receiverInfo.exported)
        assertEquals(
            "SEND_MESSAGE receiver must require signature permission",
            "io.heckel.ntfy.permission.SEND_MESSAGE",
            receiverInfo.permission
        )
    }
}
