package com.liufy.thermaldisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Best-effort boot launch. Android 10+ may restrict background Activity starts on
 * some vendor builds, so V21 also exposes MainActivity as a HOME app. Setting it
 * as the default Home/Launcher is the most reliable kiosk-style boot path.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val accepted = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!accepted) return

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { context.startActivity(launch) }
    }
}
