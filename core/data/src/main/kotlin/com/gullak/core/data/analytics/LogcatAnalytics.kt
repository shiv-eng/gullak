package com.gullak.core.data.analytics

import android.util.Log
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import javax.inject.Inject

/** Prints events to Logcat (`adb logcat -s GullakAnalytics`) so the demo can show them. */
internal class LogcatAnalytics @Inject constructor() : Analytics {
    override fun log(event: AnalyticsEvent) {
        val params = event.params.entries.joinToString(", ") { "${it.key}=${it.value}" }
        Log.i(TAG, if (params.isEmpty()) event.name else "${event.name}($params)")
    }

    private companion object {
        const val TAG = "GullakAnalytics"
    }
}
