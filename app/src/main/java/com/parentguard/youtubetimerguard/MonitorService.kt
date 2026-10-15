package com.parentguard.youtubetimerguard

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class MonitorService : Service() {
    companion object {
        private const val CHANNEL_ID = "youtube_guard_monitor"
        private const val NOTIFICATION_ID = 1515
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_KIDS_PACKAGE = "com.google.android.apps.youtube.kids"
        private const val SECRET_TAP_TARGET = 5
        private const val SECRET_TAP_WINDOW_MS = 3_000L
        const val ACTION_PARENT_UNLOCKED = "com.parentguard.youtubetimerguard.PARENT_UNLOCKED"
        const val ACTION_TEST_LOCKOUT = "com.parentguard.youtubetimerguard.TEST_LOCKOUT"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var youtubeStartedAt = 0L
    private var lastForegroundPackage: String? = null

    private lateinit var windowManager: WindowManager
    private var lockoutOverlay: FrameLayout? = null
    private var breakCountdownView: TextView? = null
    private var pinPanel: LinearLayout? = null
    private var pinDots: TextView? = null
    private val pinBuffer = StringBuilder()
    private var secretTapCount = 0
    private var firstSecretTapAt = 0L
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!Prefs.isLockoutActive(this@MonitorService)) return
            handler.post {
                muteMediaForLockout()
                showLockoutHostActivity()
                showLockoutOverlay()
                enforceLockoutUi()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                if (!Prefs.isEnabled(this@MonitorService) && !Prefs.isLockoutActive(this@MonitorService)) {
                    hideLockoutOverlay()
                    stopSelf()
                    return
                }

                if (Prefs.isLockoutActive(this@MonitorService)) {
                    if (Prefs.lockoutRemainingMillis(this@MonitorService) <= 0L) {
                        finishBreak("Break finished")
                    } else {
                        muteMediaForLockout()
                        showLockoutOverlay()
                        enforceLockoutUi()
                        updateBreakCountdown()
                    }
                } else {
                    hideLockoutOverlay()
                    updateYouTubeTimer()
                }
            } finally {
                val nextDelay = if (Prefs.isLockoutActive(this@MonitorService)) 250L else 1000L
                handler.postDelayed(this, nextDelay)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        registerScreenReceiver()
        startForeground(NOTIFICATION_ID, buildNotification("Screen-time monitoring is active"))
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PARENT_UNLOCKED -> unlockFromParent()
            ACTION_TEST_LOCKOUT -> {
                muteMediaForLockout()
                Prefs.setLockoutActive(this, true)
                showLockoutHostActivity()
                showLockoutOverlay()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
            screenReceiverRegistered = false
        }
        hideLockoutOverlay()
        if (!Prefs.isLockoutActive(this)) restoreMediaVolume()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun updateYouTubeTimer() {
        val pkg = currentForegroundPackage()
        val isYouTube = pkg == YOUTUBE_PACKAGE || pkg == YOUTUBE_KIDS_PACKAGE
        val now = SystemClock.elapsedRealtime()
        val limit = Prefs.limitMinutes(this) * 60_000L

        if (youtubeStartedAt == 0L) {
            if (isYouTube) {
                youtubeStartedAt = now
                updateNotification("Timer started")
            } else {
                updateNotification("Waiting for YouTube")
                return
            }
        }

        val elapsed = now - youtubeStartedAt
        val remainingSec = ((limit - elapsed).coerceAtLeast(0L) / 1000L)
        val statusPrefix = if (isYouTube) "YouTube" else "Timer running"
        updateNotification("${statusPrefix}: ${remainingSec / 60}:${(remainingSec % 60).toString().padStart(2, '0')} remaining")

        if (elapsed >= limit) {
            muteMediaForLockout()
            Prefs.setLockoutActive(this, true)
            showLockoutHostActivity()
            showLockoutOverlay()
            updateNotification("Break time started")
        }
    }

    private fun currentForegroundPackage(): String? {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - 12_000L, now)
        val event = UsageEvents.Event()
        var newestTime = 0L
        var newestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp >= newestTime) {
                    newestTime = event.timeStamp
                    newestPackage = event.packageName
                }
            }
        }
        if (newestPackage != null) lastForegroundPackage = newestPackage
        return lastForegroundPackage
    }

    private fun showLockoutHostActivity() {
        if (!Prefs.isLockoutActive(this)) return
        try {
            startActivity(
                Intent(this, LockoutHostActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
            )
        } catch (_: Exception) {
            updateNotification("Break active")
        }
    }

    private fun showLockoutOverlay() {
        if (lockoutOverlay != null) {
            enforceLockoutUi()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            updateNotification("Overlay permission required")
            return
        }

        pinBuffer.clear()
        resetSecretTapSequence()

        val root = LockoutFrameLayout(this).apply {
            setBackgroundColor(Color.rgb(13, 27, 42))
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            systemUiVisibility = lockoutSystemUiFlags()
            setOnSystemUiVisibilityChangeListener {
                if (Prefs.isLockoutActive(this@MonitorService)) {
                    handler.postDelayed({ enforceLockoutUi() }, 80L)
                }
            }
        }

        val content = buildBreakContent()
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        })

        val secretTapZone = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = false
            setOnClickListener { registerSecretTap() }
        }
        root.addView(secretTapZone, FrameLayout.LayoutParams(
            dp(104),
            dp(104),
            Gravity.TOP or Gravity.START
        ))

        val panel = buildPinPanel()
        pinPanel = panel
        panel.visibility = View.GONE
        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(root, params)
            lockoutOverlay = root
            root.requestFocus()
            enforceLockoutUi()
            updateBreakCountdown()
        } catch (_: Exception) {
            updateNotification("Could not show break countdown")
        }
    }

    private fun hideLockoutOverlay() {
        val view = lockoutOverlay ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        lockoutOverlay = null
        breakCountdownView = null
        pinPanel = null
        pinDots = null
        pinBuffer.clear()
        resetSecretTapSequence()
    }

    private fun buildBreakContent(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(24, 45, 67))

            addView(TextView(this@MonitorService).apply {
                text = "Screen Time Guard"
                setTextColor(Color.rgb(176, 203, 230))
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, matchWidth())

            addView(TextView(this@MonitorService).apply {
                text = "Break time"
                setTextColor(Color.WHITE)
                textSize = 30f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, dp(18), 0, 0)
            }, matchWidth())

            addView(TextView(this@MonitorService).apply {
                text = "Viewing time is finished"
                setTextColor(Color.rgb(210, 224, 238))
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(22))
            }, matchWidth())

            val countdown = TextView(this@MonitorService).apply {
                text = "--:--"
                setTextColor(Color.WHITE)
                textSize = 56f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                includeFontPadding = false
            }
            breakCountdownView = countdown
            addView(countdown, matchWidth())

            addView(TextView(this@MonitorService).apply {
                text = "YouTube and YouTube Kids are available again when the timer reaches 00:00."
                setTextColor(Color.rgb(210, 224, 238))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(18))
            }, matchWidth())

        }
    }

    private fun updateBreakCountdown() {
        val remainingMs = Prefs.lockoutRemainingMillis(this)
        val remainingSec = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val minutes = remainingSec / 60L
        val seconds = remainingSec % 60L
        val formatted = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        breakCountdownView?.text = formatted
        updateNotification("Break time: $formatted remaining")
    }

    private fun finishBreak(notificationText: String) {
        Prefs.setLockoutActive(this, false)
        restoreMediaVolume()
        hideLockoutOverlay()
        youtubeStartedAt = 0L
        lastForegroundPackage = null
        updateNotification(notificationText)
    }

    private fun muteMediaForLockout() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!Prefs.hasSavedMediaVolume(this)) {
            Prefs.saveMediaVolume(this, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (_: SecurityException) {
            updateNotification("Time limit reached; could not mute media volume")
        }
    }

    private fun restoreMediaVolume() {
        val saved = Prefs.consumeSavedMediaVolume(this) ?: return
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val safeVolume = saved.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, safeVolume, 0)
        } catch (_: SecurityException) {
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (_: Exception) {
            screenReceiverRegistered = false
        }
    }

    private fun lockoutSystemUiFlags(): Int =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

    private fun enforceLockoutUi() {
        val root = lockoutOverlay ?: return
        if (!Prefs.isLockoutActive(this)) return
        root.systemUiVisibility = lockoutSystemUiFlags()
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.requestFocus()
    }

    private fun registerSecretTap() {
        if (pinPanel?.visibility == View.VISIBLE) return
        val now = SystemClock.elapsedRealtime()
        if (firstSecretTapAt == 0L || now - firstSecretTapAt > SECRET_TAP_WINDOW_MS) {
            firstSecretTapAt = now
            secretTapCount = 1
        } else {
            secretTapCount += 1
        }

        if (secretTapCount >= SECRET_TAP_TARGET) {
            resetSecretTapSequence()
            pinBuffer.clear()
            updatePinDots()
            pinPanel?.visibility = View.VISIBLE
            pinPanel?.bringToFront()
        }
    }

    private fun resetSecretTapSequence() {
        secretTapCount = 0
        firstSecretTapAt = 0L
    }

    private inner class LockoutFrameLayout(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (Prefs.isLockoutActive(this@MonitorService)) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE -> {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            muteMediaForLockout()
                            enforceLockoutUi()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_APP_SWITCH,
                    KeyEvent.KEYCODE_POWER -> return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private fun buildPinPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(16, 16, 16))
            isClickable = true
            setOnTouchListener { _, _ -> true }
        }

        panel.addView(TextView(this).apply {
            text = "Parent unlock"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }, matchWidth().apply { bottomMargin = dp(10) })

        pinDots = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            setPadding(0, dp(8), 0, dp(12))
        }
        panel.addView(pinDots, matchWidth())

        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
            panel.addView(numberRow(row), matchWidth())
        }
        panel.addView(numberRow(listOf("Clear", "0", "Cancel")), matchWidth())

        val unlock = Button(this).apply {
            text = "UNLOCK"
            setOnClickListener {
                if (Prefs.verifyPin(this@MonitorService, pinBuffer.toString())) {
                    unlockFromParent()
                } else {
                    pinBuffer.clear()
                    updatePinDots("Incorrect PIN")
                }
            }
        }
        panel.addView(unlock, matchWidth().apply { topMargin = dp(8) })
        return panel
    }

    private fun numberRow(labels: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            labels.forEach { label ->
                addView(Button(this@MonitorService).apply {
                    text = label
                    minHeight = dp(50)
                    setOnClickListener {
                        when (label) {
                            "Clear" -> pinBuffer.clear()
                            "Cancel" -> {
                                pinBuffer.clear()
                                pinPanel?.visibility = View.GONE
                            }
                            else -> if (pinBuffer.length < 8) pinBuffer.append(label)
                        }
                        updatePinDots()
                    }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                })
            }
        }
    }

    private fun updatePinDots(error: String? = null) {
        pinDots?.text = error ?: "•".repeat(pinBuffer.length)
    }

    private fun unlockFromParent() {
        finishBreak("Parent ended break")
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen Time Guard", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Required while parental screen-time monitoring is active"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_screen_time)
            .setContentTitle("Screen Time Guard")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()


}
