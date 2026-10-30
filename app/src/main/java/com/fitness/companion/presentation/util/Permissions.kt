package com.fitness.companion.presentation.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object AppPermissions {

    /**
     * Permissions asked for when step tracking is switched on. Only activity recognition is
     * mandatory; a denied notification or body-sensor permission does not block tracking.
     */
    fun stepTracking(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        // Android 16 replaced BODY_SENSORS with Health Connect permissions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) add(Manifest.permission.BODY_SENSORS)
    }.toTypedArray()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Before Android 10 the step counter needs no runtime permission. */
    fun canTrackSteps(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)
}

/** Opens this app's system settings page, where a permanently denied permission can be granted. */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
