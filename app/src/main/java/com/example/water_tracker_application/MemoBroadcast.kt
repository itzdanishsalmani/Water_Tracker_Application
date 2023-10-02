package com.example.water_tracker_application

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MemoBroadcast : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repeatingIntent = Intent(context, NotificationOpen::class.java)
        repeatingIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(context, 0, repeatingIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, "Notification")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setLargeIcon(Bitmap.createScaledBitmap(BitmapFactory.decodeResource(context.resources, R.drawable.glass), 128, 128, false))
            .setContentTitle("Reminder")
            .setContentText("Hey! Dont forget to drink water")
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)

        notificationManager.notify(200, builder.build())
    }
}
