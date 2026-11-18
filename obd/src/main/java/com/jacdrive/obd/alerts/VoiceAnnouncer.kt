package com.jacdrive.obd.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceStatus { READY, LANGUAGE_MISSING, ENGINE_UNAVAILABLE }

/**
 * Speaks alerts with the system Text-to-Speech engine. Audio uses navigation-guidance attributes
 * and transient ducking focus, so when the phone is on Android Auto or Bluetooth the car's audio
 * system plays it over the music, like a navigation prompt.
 */
@Singleton
class VoiceAnnouncer @Inject constructor(@ApplicationContext private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()

    private suspend fun engine(): TextToSpeech? {
        tts?.let { if (ready?.await() == true) return it }
        val d = CompletableDeferred<Boolean>()
        ready = d
        val t = TextToSpeech(context) { status -> d.complete(status == TextToSpeech.SUCCESS) }
        t.setAudioAttributes(attributes)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { audio?.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { audio?.abandonAudioFocusRequest(focus) }
        })
        tts = t
        return if (withTimeoutOrNull(5_000) { d.await() } == true) t else null
    }

    suspend fun status(locale: Locale): VoiceStatus {
        val t = engine() ?: return VoiceStatus.ENGINE_UNAVAILABLE
        return when (t.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> VoiceStatus.LANGUAGE_MISSING
            else -> VoiceStatus.READY
        }
    }

    /** @return true if the text was queued for speech. */
    suspend fun speak(text: String, locale: Locale): Boolean {
        val t = engine() ?: return false
        if (t.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) return false
        audio?.requestAudioFocus(focus)
        return t.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString()) == TextToSpeech.SUCCESS
    }
}
