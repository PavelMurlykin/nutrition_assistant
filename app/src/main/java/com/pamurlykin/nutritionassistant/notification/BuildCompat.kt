package com.pamurlykin.nutritionassistant.notification

import android.os.Build

object BuildCompat {
    fun requiresNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
