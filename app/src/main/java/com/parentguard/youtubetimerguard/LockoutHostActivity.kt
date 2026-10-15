package com.parentguard.youtubetimerguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout

class LockoutHostActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private val guard = object : Runnable {
        override fun run() {
            if (!Prefs.isLockoutActive(this@LockoutHostActivity)) {
                finishAndRemoveTask()
                return
            }
            applyImmersiveMode()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isLockoutActive(this)) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(13, 27, 42))
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        })

        applyImmersiveMode()
        handler.post(guard)

        startForegroundService(Intent(this, MonitorService::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (!Prefs.isLockoutActive(this)) {
            finishAndRemoveTask()
            return
        }
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Prefs.isLockoutActive(this)) {
            applyImmersiveMode()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Prefs.isLockoutActive(this)) {
            handler.postDelayed({
                if (Prefs.isLockoutActive(this)) {
                    try {
                        startActivity(
                            Intent(this, LockoutHostActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            }
                        )
                    } catch (_: Exception) {
                    }
                }
            }, 120L)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(guard)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (Prefs.isLockoutActive(this)) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_APP_SWITCH -> true
                else -> super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!Prefs.isLockoutActive(this)) {
            super.onBackPressed()
        }
    }

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
