package com.consuelomerino.tierracolorada.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import com.consuelomerino.tierracolorada.R

/** Rake scrape volume for a given rake speed: silent when still, up to 60 % when fast. */
fun scrapeVolume(cellsPerSecond: Float): Float = (cellsPerSecond / 120f).coerceIn(0f, 1f) * 0.6f

enum class SoundEffect { STONE, WATER, SWEEP }

/**
 * Plays the ambient rainforest loop and the short interaction sounds.
 * Sound only plays while the app is in the foreground, holds audio focus, and is not muted.
 */
class GardenAudio(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
    private val effects = mapOf(
        SoundEffect.STONE to soundPool.load(appContext, R.raw.stone_place, 1),
        SoundEffect.WATER to soundPool.load(appContext, R.raw.water_tap, 1),
        SoundEffect.SWEEP to soundPool.load(appContext, R.raw.clear_sweep, 1),
    )
    private val scrapeSound = soundPool.load(appContext, R.raw.rake_scrape, 1)
    private var scrapeStream = 0

    private val ambient: MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(attributes)
            appContext.resources.openRawResourceFd(R.raw.ambient_rainforest).use {
                setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            isLooping = true
            setVolume(AMBIENT_VOLUME, AMBIENT_VOLUME)
            prepare()
        }
    }.getOrNull()

    private var foreground = false
    private var hasFocus = false
    private var muted = true

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
            updatePlayback()
        }
        .build()

    private val audible get() = foreground && hasFocus && !muted

    fun resume() {
        foreground = true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        updatePlayback()
    }

    fun pause() {
        foreground = false
        updatePlayback()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        updatePlayback()
    }

    fun setRakeSpeed(cellsPerSecond: Float) {
        val volume = scrapeVolume(cellsPerSecond)
        if (!audible || volume == 0f) {
            stopScrape()
            return
        }
        if (scrapeStream == 0) {
            scrapeStream = soundPool.play(scrapeSound, volume, volume, 1, -1, 1f)
        } else {
            soundPool.resume(scrapeStream)
            soundPool.setVolume(scrapeStream, volume, volume)
        }
    }

    fun play(effect: SoundEffect) {
        if (!audible) return
        effects[effect]?.let { soundPool.play(it, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1f) }
    }

    fun release() {
        ambient?.release()
        soundPool.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun stopScrape() {
        if (scrapeStream != 0) soundPool.pause(scrapeStream)
    }

    private fun updatePlayback() {
        val player = ambient
        if (audible) {
            if (player != null && !player.isPlaying) player.start()
        } else {
            if (player != null && player.isPlaying) player.pause()
            stopScrape()
        }
    }

    private companion object {
        const val AMBIENT_VOLUME = 0.35f
        const val EFFECT_VOLUME = 0.8f
    }
}
