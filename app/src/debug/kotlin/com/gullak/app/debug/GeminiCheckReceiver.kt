package com.gullak.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gullak.core.ai.GeminiDiagnostics
import com.gullak.core.domain.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only. Runs a real Gemini call for a sentence (default: test sentence 1) and logs the result:
 *
 * adb shell am broadcast -n com.gullak.app/.debug.GeminiCheckReceiver [--es sentence "..."]
 * adb logcat -s GullakGeminiCheck
 */
@AndroidEntryPoint
class GeminiCheckReceiver : BroadcastReceiver() {

    @Inject
    lateinit var diagnostics: GeminiDiagnostics

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val sentence = intent.getStringExtra(EXTRA_SENTENCE) ?: DEFAULT_SENTENCE
        val pending = goAsync()
        scope.launch {
            try {
                Log.i(TAG, "sentence=$sentence")
                Log.i(TAG, diagnostics.check(sentence))
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GullakGeminiCheck"
        const val EXTRA_SENTENCE = "sentence"
        const val DEFAULT_SENTENCE = "Bhai ki shaadi, agle December tak, 50 hazaar"
    }
}
