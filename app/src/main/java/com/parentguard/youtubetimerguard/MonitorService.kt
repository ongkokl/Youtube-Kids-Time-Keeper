package com.parentguard.youtubetimerguard

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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
        private const val SHUTDOWN_MESSAGE_DURATION_MS = 15_000L
        const val ACTION_PARENT_UNLOCKED = "com.parentguard.youtubetimerguard.PARENT_UNLOCKED"
        const val ACTION_TEST_LOCKOUT = "com.parentguard.youtubetimerguard.TEST_LOCKOUT"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var youtubeStartedAt = 0L
    private var lastForegroundPackage: String? = null

    private lateinit var windowManager: WindowManager
    private var lockoutOverlay: FrameLayout? = null
    private var shutdownStatusView: LinearLayout? = null
    private var fakeBatteryStatusView: LinearLayout? = null
    private var countdownContainer: LinearLayout? = null
    private var countdownTimeText: TextView? = null
    private var mascotView: TextView? = null
    private var mascotNameView: TextView? = null
    private var lastMascotSlot = -1L
    private var lastMascotIndex = -1
    private var pinPanel: LinearLayout? = null
    private var pinDots: TextView? = null
    private val pinBuffer = StringBuilder()
    private var tapCount = 0
    private var firstTapAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            try {
                if (!Prefs.isEnabled(this@MonitorService) && !Prefs.isLockoutActive(this@MonitorService)) {
                    hideLockoutOverlay()
                    stopSelf()
                    return
                }

                if (Prefs.isLockoutActive(this@MonitorService)) {
                    handleLockoutTick()
                } else {
                    hideLockoutOverlay()
                    updateYouTubeTimer()
                }
            } finally {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Watching for YouTube use"))
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PARENT_UNLOCKED -> unlockFromParent()
            ACTION_TEST_LOCKOUT -> beginLockout()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
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
            beginLockout()
        }
    }

    private fun beginLockout() {
        val now = System.currentTimeMillis()
        val countdownStart = now + SHUTDOWN_MESSAGE_DURATION_MS
        val countdownEnd = countdownStart + Prefs.lockoutMinutes(this) * 60_000L
        Prefs.startLockout(this, countdownStart, countdownEnd)
        muteMediaForLockout()
        showLockoutOverlay()
        updateLockoutPresentation()
        updateNotification("Time limit reached")
    }

    private fun handleLockoutTick() {
        ensureLockoutWindow()
        val endMs = Prefs.lockoutEndTimeMs(this)
        val now = System.currentTimeMillis()

        if (endMs > 0L && now >= endMs) {
            finishLockoutAutomatically()
            return
        }

        muteMediaForLockout()
        showLockoutOverlay()
        updateLockoutPresentation()
    }

    private fun ensureLockoutWindow() {
        if (Prefs.lockoutCountdownStartMs(this) > 0L && Prefs.lockoutEndTimeMs(this) > 0L) return
        val now = System.currentTimeMillis()
        val countdownStart = now + SHUTDOWN_MESSAGE_DURATION_MS
        val countdownEnd = countdownStart + Prefs.lockoutMinutes(this) * 60_000L
        Prefs.startLockout(this, countdownStart, countdownEnd)
    }

    private fun updateLockoutPresentation() {
        val now = System.currentTimeMillis()
        val countdownStart = Prefs.lockoutCountdownStartMs(this)
        val endMs = Prefs.lockoutEndTimeMs(this)

        if (countdownStart <= 0L || endMs <= 0L) return

        if (now < countdownStart) {
            shutdownStatusView?.visibility = View.VISIBLE
            fakeBatteryStatusView?.visibility = View.VISIBLE
            countdownContainer?.visibility = View.GONE
            return
        }

        shutdownStatusView?.visibility = View.GONE
        fakeBatteryStatusView?.visibility = View.GONE
        countdownContainer?.visibility = View.VISIBLE

        val remainingMs = (endMs - now).coerceAtLeast(0L)
        val totalSeconds = (remainingMs + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val display = if (hours > 0L) {
            "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
        countdownTimeText?.text = display
        updateMascot(now, countdownStart)
        updateNotification("Break: $display remaining")
    }

    private fun finishLockoutAutomatically() {
        Prefs.clearLockout(this)
        restoreMediaVolume()
        hideLockoutOverlay()
        youtubeStartedAt = 0L
        lastForegroundPackage = null
        updateNotification("Break complete — YouTube available")
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

    private fun showLockoutOverlay() {
        if (lockoutOverlay != null) return
        if (!Settings.canDrawOverlays(this)) {
            updateNotification("Overlay permission required")
            return
        }

        pinBuffer.clear()
        tapCount = 0
        firstTapAt = 0L

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && pinPanel?.visibility != View.VISIBLE) {
                    val inSecretCorner = event.x < resources.displayMetrics.widthPixels * 0.25f &&
                        event.y < resources.displayMetrics.heightPixels * 0.25f
                    if (inSecretCorner) registerSecretTap()
                }
                true
            }
        }

        @Suppress("DEPRECATION")
        root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val fakeBattery = buildFakeBatteryStatusView()
        fakeBatteryStatusView = fakeBattery
        root.addView(fakeBattery, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(14)
            marginEnd = dp(18)
        })

        val shutdownStatus = buildShutdownStatusView()
        shutdownStatusView = shutdownStatus
        root.addView(shutdownStatus, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = (resources.displayMetrics.heightPixels * 0.34f).toInt()
        })

        val countdown = buildCountdownView()
        countdownContainer = countdown
        countdown.visibility = View.GONE
        root.addView(countdown, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        })

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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenBrightness = 0.01f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(root, params)
            lockoutOverlay = root
            updateLockoutPresentation()
        } catch (_: Exception) {
            updateNotification("Could not show fake shutdown")
        }
    }

    private fun hideLockoutOverlay() {
        val view = lockoutOverlay ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        lockoutOverlay = null
        shutdownStatusView = null
        fakeBatteryStatusView = null
        countdownContainer = null
        countdownTimeText = null
        mascotView = null
        mascotNameView = null
        lastMascotSlot = -1L
        lastMascotIndex = -1
        pinPanel = null
        pinDots = null
        pinBuffer.clear()
    }

    private fun buildShutdownStatusView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)

            addView(TextView(this@MonitorService).apply {
                text = "Shutting down..."
                setTextColor(Color.rgb(245, 245, 245))
                textSize = 24f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.01f
                includeFontPadding = false
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            addView(ShutdownSpinnerView(this@MonitorService), LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(22)
            })
        }
    }

    private fun buildCountdownView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)

            mascotView = TextView(this@MonitorService).apply {
                text = "🦖"
                textSize = 88f
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            addView(mascotView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            mascotNameView = TextView(this@MonitorService).apply {
                text = "Dino Buddy"
                setTextColor(Color.rgb(210, 210, 210))
                textSize = 20f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                setPadding(0, dp(4), 0, dp(20))
            }
            addView(mascotNameView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            addView(TextView(this@MonitorService).apply {
                text = "YouTube available again in"
                setTextColor(Color.rgb(235, 235, 235))
                textSize = 30f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            countdownTimeText = TextView(this@MonitorService).apply {
                text = "--:--"
                setTextColor(Color.WHITE)
                textSize = 98f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                includeFontPadding = false
                letterSpacing = 0.03f
                setPadding(0, dp(12), 0, dp(6))
            }
            addView(countdownTimeText, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            addView(TextView(this@MonitorService).apply {
                text = "Take a break"
                setTextColor(Color.rgb(185, 185, 185))
                textSize = 24f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                includeFontPadding = false
                setPadding(0, dp(8), 0, 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun updateMascot(nowMs: Long, countdownStartMs: Long) {
        val slot = ((nowMs - countdownStartMs).coerceAtLeast(0L)) / 7_000L
        if (slot == lastMascotSlot) return
        lastMascotSlot = slot

        val mascots = arrayOf(
            "🦖" to "Dino Buddy",
            "🐼" to "Panda Pal",
            "🤖" to "Robo Buddy",
            "🐰" to "Bunny Bounce",
            "🐱" to "Kitty Star",
            "🦊" to "Foxy Friend",
            "🐯" to "Tiger Hero",
            "🚀" to "Space Explorer"
        )

        var next = kotlin.random.Random.nextInt(mascots.size)
        if (mascots.size > 1 && next == lastMascotIndex) {
            next = (next + 1) % mascots.size
        }
        lastMascotIndex = next
        val mascot = mascots[next]

        mascotView?.animate()?.cancel()
        mascotNameView?.animate()?.cancel()
        mascotView?.alpha = 0f
        mascotNameView?.alpha = 0f
        mascotView?.text = mascot.first
        mascotNameView?.text = mascot.second
        mascotView?.animate()?.alpha(1f)?.setDuration(350L)?.start()
        mascotNameView?.animate()?.alpha(1f)?.setDuration(350L)?.start()
    }

    private fun buildFakeBatteryStatusView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(Color.BLACK)

            addView(TextView(this@MonitorService).apply {
                text = "0%"
                setTextColor(Color.rgb(235, 235, 235))
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(5) })

            addView(EmptyBatteryView(this@MonitorService), LinearLayout.LayoutParams(dp(24), dp(12)))
        }
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

    private fun registerSecretTap() {
        val now = SystemClock.elapsedRealtime()
        if (firstTapAt == 0L || now - firstTapAt > 3000L) {
            firstTapAt = now
            tapCount = 1
        } else {
            tapCount++
        }
        if (tapCount >= 5) {
            tapCount = 0
            firstTapAt = 0L
            pinBuffer.clear()
            updatePinDots()
            pinPanel?.visibility = View.VISIBLE
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
        Prefs.clearLockout(this)
        restoreMediaVolume()
        hideLockoutOverlay()
        youtubeStartedAt = 0L
        lastForegroundPackage = null
        updateNotification("Monitoring resumed")
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "YouTube Timer Guard", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Required while the YouTube timer is active"
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
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("YouTube Timer Guard")
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

    private class EmptyBatteryView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = Color.rgb(235, 235, 235)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = paint.strokeWidth
            val nubWidth = 2.5f * density
            val body = RectF(
                stroke,
                stroke,
                width - nubWidth - stroke,
                height - stroke
            )
            canvas.drawRoundRect(body, 2f * density, 2f * density, paint)
            val nubLeft = body.right + stroke
            val nub = RectF(
                nubLeft,
                height * 0.34f,
                width - stroke,
                height * 0.66f
            )
            canvas.drawRect(nub, paint)
        }
    }

    private class ShutdownSpinnerView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.8f * density
            color = Color.WHITE
        }
        private var angle = 0f
        private val spin = object : Runnable {
            override fun run() {
                angle = (angle + 12f) % 360f
                invalidate()
                postDelayed(this, 16L)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            removeCallbacks(spin)
            post(spin)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(spin)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = paint.strokeWidth / 2f + density
            val oval = RectF(inset, inset, width - inset, height - inset)
            canvas.drawArc(oval, angle, 72f, false, paint)
        }
    }
}
