package com.chue.ytkeep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Entry point for the separate OK ผู้ช่วย app. Playback remains owned by YTNx. */
class VoiceCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(action)
        intent.getStringExtra("playlist_name")?.let { launch.putExtra("playlist_name", it) }
        intent.getStringExtra("command")?.let { launch.putExtra("command", it) }
        context.startActivity(launch)
    }
}
