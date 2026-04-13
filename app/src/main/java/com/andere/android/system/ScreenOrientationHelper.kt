package com.andere.android.system

import android.content.Context
import android.content.res.Configuration

object ScreenOrientationHelper {
    /**
     * Returns true if the system is currently in landscape orientation.
     *
     * When called with Application context, `resources.configuration` reflects
     * the system-level orientation (respecting the rotation lock setting),
     * NOT a single foreground app's forced orientation.
     */
    fun isCurrentLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
