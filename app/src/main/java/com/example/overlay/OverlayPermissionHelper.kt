package com.example.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object OverlayPermissionHelper {

  fun canDrawOverlays(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
  }

  fun createOverlaySettingsIntent(context: Context): Intent {
    return Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:${context.packageName}"),
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun startOverlayService(context: Context, action: String = OverlayActions.ACTION_START_OR_SHOW) {
    if (!canDrawOverlays(context)) return
    val intent = Intent(context, FloatingOverlayService::class.java).apply {
      this.action = action
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      ContextCompat.startForegroundService(context, intent)
    } else {
      context.startService(intent)
    }
  }

  fun hideOverlayTemporarily(context: Context) {
    val intent = Intent(context, FloatingOverlayService::class.java).apply {
      action = OverlayActions.ACTION_HIDE_TEMPORARILY
    }
    context.startService(intent)
  }

  fun stopOverlayService(context: Context) {
    val intent = Intent(context, FloatingOverlayService::class.java)
    context.stopService(intent)
  }
}
