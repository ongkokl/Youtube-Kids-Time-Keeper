package com.parentguard.youtubetimerguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var minutesInput: EditText
    private lateinit var monitorButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "YouTube Timer Guard"
        buildUi()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(248, 248, 248))
        }

        root.addView(TextView(this).apply {
            text = "YouTube Timer Guard"
            textSize = 26f
            setTextColor(Color.BLACK)
        })

        root.addView(TextView(this).apply {
            text = "After continuous YouTube use reaches the limit, the phone shows a fullscreen black fake-shutdown screen until the parent PIN is entered."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(12), 0, dp(18))
        })

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(statusText)

        root.addView(Button(this).apply {
            text = "1. Grant Usage Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "2. Allow Display Over Other Apps"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "3. Set / Change Parent PIN"
            setOnClickListener { showSetPinDialog() }
        }, matchWidth())

        val durationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
        }
        durationRow.addView(TextView(this).apply {
            text = "YouTube limit (minutes):"
            textSize = 16f
            setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        minutesInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(Prefs.limitMinutes(this@MainActivity).toString())
            gravity = Gravity.CENTER
        }
        durationRow.addView(minutesInput, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(durationRow)

        monitorButton = Button(this).apply {
            setOnClickListener { toggleMonitoring() }
        }
        root.addView(monitorButton, matchWidth())

        root.addView(Button(this).apply {
            text = "Test Fake Shutdown"
            setOnClickListener {
                if (!Prefs.hasPin(this@MainActivity)) {
                    toast("Set a parent PIN first")
                    return@setOnClickListener
                }
                Prefs.setLockoutActive(this@MainActivity, true)
                if (!Prefs.isEnabled(this@MainActivity)) {
                    Prefs.setEnabled(this@MainActivity, true)
                }
                startForegroundService(
                    Intent(this@MainActivity, MonitorService::class.java)
                        .setAction(MonitorService.ACTION_TEST_LOCKOUT)
                )
            }
        }, matchWidth())

        root.addView(TextView(this).apply {
            text = "Parent unlock: tap the top-left corner 5 times quickly, then enter the parent PIN. Power/emergency system controls remain available."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(18), 0, dp(18))
        })

        root.addView(TextView(this).apply {
            text = "Designed by Ong KL"
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.rgb(110, 110, 110))
            setPadding(0, dp(8), 0, dp(8))
        }, matchWidth())

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun toggleMonitoring() {
        if (Prefs.isEnabled(this)) {
            Prefs.setEnabled(this, false)
            Prefs.setLockoutActive(this, false)
            stopService(Intent(this, MonitorService::class.java))
            refreshStatus()
            return
        }

        if (!hasUsageAccess()) {
            toast("Grant Usage Access first")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow Display Over Other Apps first")
            return
        }
        if (!Prefs.hasPin(this)) {
            toast("Set a parent PIN first")
            return
        }

        val minutes = minutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 15
        Prefs.setLimitMinutes(this, minutes)
        Prefs.setLockoutActive(this, false)
        Prefs.setEnabled(this, true)
        startForegroundService(Intent(this, MonitorService::class.java))
        refreshStatus()
    }

    private fun showSetPinDialog() {
        val field = EditText(this).apply {
            hint = "4–8 digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val container = LinearLayout(this).apply {
            setPadding(dp(24), 0, dp(24), 0)
            addView(field, matchWidth())
        }
        AlertDialog.Builder(this)
            .setTitle("Parent PIN")
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val pin = field.text.toString()
                        if (!pin.matches(Regex("\\d{4,8}"))) {
                            field.error = "Use 4–8 digits"
                        } else {
                            Prefs.setPin(this, pin)
                            dialog.dismiss()
                            refreshStatus()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun refreshStatus() {
        val enabled = Prefs.isEnabled(this)
        statusText.text = buildString {
            append("Usage Access: ").append(if (hasUsageAccess()) "Granted" else "Not granted")
            append("\nDisplay over apps: ").append(if (Settings.canDrawOverlays(this@MainActivity)) "Granted" else "Not granted")
            append("\nParent PIN: ").append(if (Prefs.hasPin(this@MainActivity)) "Set" else "Not set")
            append("\nMonitoring: ").append(if (enabled) "ON" else "OFF")
            if (Prefs.isLockoutActive(this@MainActivity)) append("\nFake shutdown: ACTIVE")
        }
        monitorButton.text = if (enabled) "Stop Monitoring" else "Start Monitoring"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 50)
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(8)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
