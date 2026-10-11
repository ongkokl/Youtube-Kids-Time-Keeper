package com.parentguard.youtubetimerguard

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object Prefs {
    private const val FILE = "guard_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LIMIT_MINUTES = "limit_minutes"
    private const val KEY_BREAK_MINUTES = "break_minutes"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_LOCKOUT = "lockout_active"
    private const val KEY_LOCKOUT_END_EPOCH_MS = "lockout_end_epoch_ms"
    private const val KEY_SAVED_MEDIA_VOLUME = "saved_media_volume"
    private const val KEY_HAS_SAVED_MEDIA_VOLUME = "has_saved_media_volume"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun limitMinutes(context: Context): Int = prefs(context).getInt(KEY_LIMIT_MINUTES, 15).coerceIn(1, 180)
    fun setLimitMinutes(context: Context, minutes: Int) =
        prefs(context).edit().putInt(KEY_LIMIT_MINUTES, minutes.coerceIn(1, 180)).apply()

    fun breakMinutes(context: Context): Int = prefs(context).getInt(KEY_BREAK_MINUTES, 30).coerceIn(1, 180)
    fun setBreakMinutes(context: Context, minutes: Int) =
        prefs(context).edit().putInt(KEY_BREAK_MINUTES, minutes.coerceIn(1, 180)).apply()

    fun isLockoutActive(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCKOUT, false)

    fun setLockoutActive(context: Context, value: Boolean) {
        val p = prefs(context)
        if (value) {
            val currentEnd = p.getLong(KEY_LOCKOUT_END_EPOCH_MS, 0L)
            val end = if (currentEnd > System.currentTimeMillis()) {
                currentEnd
            } else {
                System.currentTimeMillis() + breakMinutes(context) * 60_000L
            }
            p.edit()
                .putBoolean(KEY_LOCKOUT, true)
                .putLong(KEY_LOCKOUT_END_EPOCH_MS, end)
                .apply()
        } else {
            p.edit()
                .putBoolean(KEY_LOCKOUT, false)
                .remove(KEY_LOCKOUT_END_EPOCH_MS)
                .apply()
        }
    }

    fun lockoutEndEpochMs(context: Context): Long =
        prefs(context).getLong(KEY_LOCKOUT_END_EPOCH_MS, 0L)

    fun lockoutRemainingMillis(context: Context): Long =
        (lockoutEndEpochMs(context) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun hasPin(context: Context): Boolean = prefs(context).contains(KEY_PIN_HASH)

    fun hasSavedMediaVolume(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_SAVED_MEDIA_VOLUME, false)

    fun saveMediaVolume(context: Context, volume: Int) {
        prefs(context).edit()
            .putInt(KEY_SAVED_MEDIA_VOLUME, volume.coerceAtLeast(0))
            .putBoolean(KEY_HAS_SAVED_MEDIA_VOLUME, true)
            .apply()
    }

    fun consumeSavedMediaVolume(context: Context): Int? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_HAS_SAVED_MEDIA_VOLUME, false)) return null
        val value = p.getInt(KEY_SAVED_MEDIA_VOLUME, 0)
        p.edit()
            .remove(KEY_SAVED_MEDIA_VOLUME)
            .putBoolean(KEY_HAS_SAVED_MEDIA_VOLUME, false)
            .apply()
        return value
    }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(salt, pin)
        prefs(context).edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hash.toHex())
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val saltHex = prefs(context).getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        val actual = hash(saltHex.hexToBytes(), pin).toHex()
        return MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
    }

    private fun hash(salt: ByteArray, pin: String): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(salt)
        digest(pin.toByteArray(Charsets.UTF_8))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
