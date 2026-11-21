package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.overlay.FloatingOverlayService
import com.example.overlay.OverlayActions

object NotificationHelper {
  const val OVERLAY_CHANNEL_ID = "floattask_overlay_channel"
  const val REMINDER_CHANNEL_ID = "floattask_reminder_channel"
  const val OVERLAY_NOTIFICATION_ID = 1001
  const val DAILY_REMINDER_NOTIFICATION_ID = 2001

  fun createChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return

      val overlayChannel =
        NotificationChannel(
            OVERLAY_CHANNEL_ID,
            context.getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = context.getString(R.string.overlay_channel_desc)
            setShowBadge(false)
          }

      val reminderChannel =
        NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = context.getString(R.string.reminder_channel_desc)
            enableVibration(true)
          }

      manager.createNotificationChannel(overlayChannel)
      manager.createNotificationChannel(reminderChannel)
    }
  }

  fun buildOverlayForegroundNotification(
    context: Context,
    pendingCount: Int,
    isTemporarilyHidden: Boolean,
  ): Notification {
    val openAppIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val openAppPendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val toggleHideIntent =
      Intent(context, FloatingOverlayService::class.java).apply {
        action =
          if (isTemporarilyHidden) OverlayActions.ACTION_SHOW_OVERLAY
          else OverlayActions.ACTION_HIDE_TEMPORARILY
      }
    val toggleHidePendingIntent =
      PendingIntent.getService(
        context,
        1,
        toggleHideIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(context, FloatingOverlayService::class.java).apply {
        action = OverlayActions.ACTION_STOP_OVERLAY
      }
    val stopPendingIntent =
      PendingIntent.getService(
        context,
        2,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val statusText =
      if (isTemporarilyHidden) {
        "Floating bubble is temporarily hidden · $pendingCount pending"
      } else if (pendingCount == 0) {
        "All tasks completed · Tap to open FloatTask"
      } else {
        "$pendingCount pending ${if (pendingCount == 1) "task" else "tasks"} floating on screen"
      }

    return NotificationCompat.Builder(context, OVERLAY_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_floattask)
      .setContentTitle("FloatTask Overlay Active")
      .setContentText(statusText)
      .setContentIntent(openAppPendingIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .addAction(
        0,
        if (isTemporarilyHidden) "Show Bubble" else "Hide Temporarily",
        toggleHidePendingIntent,
      )
      .addAction(0, "Stop", stopPendingIntent)
      .build()
  }

  fun showTaskReminderNotification(
    context: Context,
    notificationId: Int,
    title: String,
    body: String,
  ) {
    if (!hasNotificationPermission(context)) return

    val openIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_floattask)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}
