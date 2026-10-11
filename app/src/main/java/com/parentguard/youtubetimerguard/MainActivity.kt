package com.parentguard.youtubetimerguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private val COLOR_BACKGROUND = Color.parseColor("#F8FAFD")
        private val COLOR_SURFACE = Color.WHITE
        private val COLOR_PRIMARY = Color.parseColor("#0B57D0")
        private val COLOR_ON_PRIMARY = Color.WHITE
        private val COLOR_TEXT = Color.parseColor("#1F1F1F")
        private val COLOR_TEXT_SECONDARY = Color.parseColor("#5F6368")
        private val COLOR_OUTLINE = Color.parseColor("#DADCE0")
        private val COLOR_SURFACE_VARIANT = Color.parseColor("#F1F3F4")
        private val COLOR_PRIMARY_CONTAINER = Color.parseColor("#E8F0FE")
        private val COLOR_SUCCESS = Color.parseColor("#137333")
        private val COLOR_SUCCESS_CONTAINER = Color.parseColor("#E6F4EA")
        private val COLOR_WARNING = Color.parseColor("#B06000")
        private val COLOR_WARNING_CONTAINER = Color.parseColor("#FEF7E0")
        private val COLOR_ERROR = Color.parseColor("#B3261E")
        private val COLOR_ERROR_CONTAINER = Color.parseColor("#FCE8E6")
    }

    private lateinit var minutesInput: EditText
    private lateinit var breakMinutesInput: EditText
    private lateinit var monitorButton: TextView
    private lateinit var monitoringCard: LinearLayout
    private lateinit var monitoringTitle: TextView
    private lateinit var monitoringDescription: TextView
    private lateinit var usageStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var pinStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        buildUi()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun configureSystemBars() {
        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(32))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        content.addView(buildHeader())
        content.addView(space(18))

        monitoringCard = card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        monitoringTitle = text("Monitoring is off", 20f, COLOR_TEXT, Typeface.BOLD)
        monitoringDescription = text(
            "Complete setup, choose viewing and break limits, then start monitoring.",
            14f,
            COLOR_TEXT_SECONDARY
        ).apply { setPadding(0, dp(6), 0, 0) }
        monitoringCard.addView(monitoringTitle)
        monitoringCard.addView(monitoringDescription)
        content.addView(monitoringCard, matchWidth())

        content.addView(sectionTitle("Setup", "Required permissions and parent access"))
        val setupCard = card()
        usageStatus = statusChip()
        overlayStatus = statusChip()
        pinStatus = statusChip()
        setupCard.addView(settingRow(
            title = "Usage access",
            subtitle = "Detects when YouTube or YouTube Kids is in the foreground",
            status = usageStatus,
            onClick = { showUsageAccessDisclosure() }
        ))
        setupCard.addView(divider())
        setupCard.addView(settingRow(
            title = "Display over apps",
            subtitle = "Shows the break countdown when viewing time is up",
            status = overlayStatus,
            onClick = { showOverlayDisclosure() }
        ))
        setupCard.addView(divider())
        setupCard.addView(settingRow(
            title = "Parent PIN",
            subtitle = "Lets a parent end a break early",
            status = pinStatus,
            onClick = { showSetPinDialog() }
        ))
        content.addView(setupCard, matchWidth())

        content.addView(sectionTitle("Viewing & break limits", "Set viewing time and required break time"))
        val timeCard = card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        timeCard.addView(text("Viewing limit", 16f, COLOR_TEXT, Typeface.BOLD))
        timeCard.addView(text(
            "Once started, the viewing timer continues even if the supported video app is minimized.",
            14f,
            COLOR_TEXT_SECONDARY
        ).apply { setPadding(0, dp(4), 0, dp(16)) })

        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val minus = stepButton("−") { changeMinutes(-1) }
        val plus = stepButton("+") { changeMinutes(1) }
        stepper.addView(minus, LinearLayout.LayoutParams(dp(48), dp(48)))

        val valueBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(COLOR_SURFACE_VARIANT, 18f)
            setPadding(dp(10), 0, dp(10), 0)
        }
        minutesInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(Prefs.limitMinutes(this@MainActivity).toString())
            gravity = Gravity.CENTER
            textSize = 26f
            setTextColor(COLOR_TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = null
            setSelectAllOnFocus(true)
            setPadding(0, 0, 0, 0)
        }
        valueBox.addView(minutesInput, LinearLayout.LayoutParams(dp(70), dp(56)))
        valueBox.addView(text("min", 14f, COLOR_TEXT_SECONDARY))
        stepper.addView(valueBox, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        })
        stepper.addView(plus, LinearLayout.LayoutParams(dp(48), dp(48)))
        timeCard.addView(stepper, matchWidth())
        content.addView(timeCard, matchWidth())

        content.addView(space(12))
        val breakCard = card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        breakCard.addView(text("Break duration", 16f, COLOR_TEXT, Typeface.BOLD))
        breakCard.addView(text(
            "When viewing time is up, the break countdown blocks YouTube and YouTube Kids while other apps remain available.",
            14f,
            COLOR_TEXT_SECONDARY
        ).apply { setPadding(0, dp(4), 0, dp(16)) })

        val breakStepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val breakMinus = stepButton("−") { changeBreakMinutes(-1) }
        val breakPlus = stepButton("+") { changeBreakMinutes(1) }
        breakStepper.addView(breakMinus, LinearLayout.LayoutParams(dp(48), dp(48)))

        val breakValueBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(COLOR_SURFACE_VARIANT, 18f)
            setPadding(dp(10), 0, dp(10), 0)
        }
        breakMinutesInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(Prefs.breakMinutes(this@MainActivity).toString())
            gravity = Gravity.CENTER
            textSize = 26f
            setTextColor(COLOR_TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = null
            setSelectAllOnFocus(true)
            setPadding(0, 0, 0, 0)
        }
        breakValueBox.addView(breakMinutesInput, LinearLayout.LayoutParams(dp(70), dp(56)))
        breakValueBox.addView(text("min", 14f, COLOR_TEXT_SECONDARY))
        breakStepper.addView(breakValueBox, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        })
        breakStepper.addView(breakPlus, LinearLayout.LayoutParams(dp(48), dp(48)))
        breakCard.addView(breakStepper, matchWidth())
        content.addView(breakCard, matchWidth())

        content.addView(space(20))
        monitorButton = actionButton("Start monitoring", primary = true) { toggleMonitoring() }
        content.addView(monitorButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ))

        content.addView(space(10))
        content.addView(actionButton("Test break countdown", primary = false) {
            testBreakCountdown()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))

        content.addView(sectionTitle("Parent access", "End a break early with the parent PIN"))
        val parentCard = card().apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        val parentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        parentRow.addView(TextView(this).apply {
            text = "🔒"
            textSize = 22f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        val parentText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        parentText.addView(text("Parent unlock", 15f, COLOR_TEXT, Typeface.BOLD))
        parentText.addView(text(
            "On the break screen, tap Parent unlock and enter the parent PIN.",
            14f,
            COLOR_TEXT_SECONDARY
        ).apply { setPadding(0, dp(4), 0, 0) })
        parentRow.addView(parentText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parentCard.addView(parentRow)
        content.addView(parentCard, matchWidth())

        content.addView(space(26))
        content.addView(text("Designed by Ong KL", 13f, COLOR_TEXT_SECONDARY, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }, matchWidth())
        content.addView(text("Screen Time Guard · v0.2.2", 12f, COLOR_TEXT_SECONDARY).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }, matchWidth())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(COLOR_BACKGROUND)
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }
        setContentView(scroll)
    }

    private fun buildHeader(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconFrame = FrameLayout(this).apply {
            background = rounded(Color.WHITE, 22f, COLOR_OUTLINE)
            elevation = dp(1).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.kid_safe_icon_v022)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.LTGRAY, 18f)
            clipToOutline = true
        }
        iconFrame.addView(icon, FrameLayout.LayoutParams(dp(56), dp(56)))
        row.addView(iconFrame, LinearLayout.LayoutParams(dp(64), dp(64)))

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        copy.addView(text("Screen Time Guard", 24f, COLOR_TEXT, Typeface.BOLD))
        copy.addView(text(
            "Parental screen-time control for supported video apps",
            14f,
            COLOR_TEXT_SECONDARY
        ).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun sectionTitle(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(24), dp(2), dp(10))
            addView(text(title, 18f, COLOR_TEXT, Typeface.BOLD))
            addView(text(subtitle, 13f, COLOR_TEXT_SECONDARY).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun settingRow(
        title: String,
        subtitle: String,
        status: TextView,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(dp(18), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(ColorStateList.valueOf(0x12000000), null, null)
            setOnClickListener { onClick() }

            val copy = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            copy.addView(text(title, 15f, COLOR_TEXT, Typeface.BOLD))
            copy.addView(text(subtitle, 13f, COLOR_TEXT_SECONDARY).apply {
                setPadding(0, dp(3), dp(8), 0)
            })
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(status)
            addView(text("›", 28f, COLOR_TEXT_SECONDARY).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(dp(28), dp(40)))
        }
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
        val breakMinutes = breakMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 30
        minutesInput.setText(minutes.toString())
        breakMinutesInput.setText(breakMinutes.toString())
        Prefs.setLimitMinutes(this, minutes)
        Prefs.setBreakMinutes(this, breakMinutes)
        Prefs.setLockoutActive(this, false)
        Prefs.setEnabled(this, true)
        startForegroundService(Intent(this, MonitorService::class.java))
        refreshStatus()
    }

    private fun testBreakCountdown() {
        if (!Prefs.hasPin(this)) {
            toast("Set a parent PIN first")
            return
        }
        val breakMinutes = breakMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 30
        breakMinutesInput.setText(breakMinutes.toString())
        Prefs.setBreakMinutes(this, breakMinutes)
        Prefs.setLockoutActive(this, true)
        if (!Prefs.isEnabled(this)) Prefs.setEnabled(this, true)
        startForegroundService(
            Intent(this, MonitorService::class.java)
                .setAction(MonitorService.ACTION_TEST_LOCKOUT)
        )
    }

    private fun changeMinutes(delta: Int) {
        val current = minutesInput.text.toString().toIntOrNull() ?: Prefs.limitMinutes(this)
        val next = (current + delta).coerceIn(1, 180)
        minutesInput.setText(next.toString())
        minutesInput.setSelection(minutesInput.text.length)
    }

    private fun changeBreakMinutes(delta: Int) {
        val current = breakMinutesInput.text.toString().toIntOrNull() ?: Prefs.breakMinutes(this)
        val next = (current + delta).coerceIn(1, 180)
        breakMinutesInput.setText(next.toString())
        breakMinutesInput.setSelection(breakMinutesInput.text.length)
    }

    private fun showUsageAccessDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Usage Access")
            .setMessage(
                "Screen Time Guard checks only whether YouTube or YouTube Kids is currently in the foreground so it can enforce the viewing limit and break countdown. This information stays on this device and is not sent to the developer or third parties."
            )
            .setPositiveButton("Continue") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverlayDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Display over apps")
            .setMessage(
                "During a parent-configured break, Screen Time Guard displays its countdown over YouTube or YouTube Kids. Other apps remain available. The overlay is removed automatically when the break ends or a parent ends it with the PIN."
            )
            .setPositiveButton("Continue") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSetPinDialog() {
        val field = EditText(this).apply {
            hint = "4–8 digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 18f
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(COLOR_SURFACE_VARIANT, 14f, COLOR_OUTLINE)
        }
        val container = LinearLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(field, matchWidth())
        }
        AlertDialog.Builder(this)
            .setTitle("Parent PIN")
            .setMessage("Use 4–8 digits. Keep this PIN private.")
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                        setTextColor(COLOR_PRIMARY)
                        setOnClickListener {
                            val pin = field.text.toString()
                            if (!pin.matches(Regex("\\d{4,8}"))) {
                                field.error = "Use 4–8 digits"
                            } else {
                                Prefs.setPin(this@MainActivity, pin)
                                dialog.dismiss()
                                refreshStatus()
                            }
                        }
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_PRIMARY)
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
        val usageGranted = hasUsageAccess()
        val overlayGranted = Settings.canDrawOverlays(this)
        val pinSet = Prefs.hasPin(this)
        val enabled = Prefs.isEnabled(this)
        val lockout = Prefs.isLockoutActive(this)

        setChip(usageStatus, usageGranted, if (usageGranted) "Granted" else "Set up")
        setChip(overlayStatus, overlayGranted, if (overlayGranted) "Granted" else "Set up")
        setChip(pinStatus, pinSet, if (pinSet) "Set" else "Set up")

        if (lockout) {
            monitoringCard.background = rounded(COLOR_ERROR_CONTAINER, 22f)
            monitoringTitle.text = "Break time active"
            monitoringTitle.setTextColor(COLOR_ERROR)
            val remaining = Prefs.lockoutRemainingMillis(this) / 1000L
            monitoringDescription.text = "Break countdown: ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}. Parent PIN can end it early."
            monitoringDescription.setTextColor(COLOR_ERROR)
        } else if (enabled) {
            monitoringCard.background = rounded(COLOR_PRIMARY_CONTAINER, 22f)
            monitoringTitle.text = "Monitoring is on"
            monitoringTitle.setTextColor(COLOR_PRIMARY)
            monitoringDescription.text = "Viewing: ${Prefs.limitMinutes(this)} min, then ${Prefs.breakMinutes(this)} min break."
            monitoringDescription.setTextColor(COLOR_TEXT_SECONDARY)
        } else {
            monitoringCard.background = rounded(COLOR_SURFACE_VARIANT, 22f)
            monitoringTitle.text = "Monitoring is off"
            monitoringTitle.setTextColor(COLOR_TEXT)
            monitoringDescription.text = if (usageGranted && overlayGranted && pinSet) {
                "Everything is ready. Choose viewing and break limits, then start monitoring."
            } else {
                "Complete the setup items below before starting monitoring."
            }
            monitoringDescription.setTextColor(COLOR_TEXT_SECONDARY)
        }

        if (enabled) {
            styleActionButton(monitorButton, primary = false, destructive = true)
            monitorButton.text = "Stop monitoring"
        } else {
            styleActionButton(monitorButton, primary = true, destructive = false)
            monitorButton.text = "Start monitoring"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 50)
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(COLOR_SURFACE, 22f, COLOR_OUTLINE)
        elevation = dp(1).toFloat()
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(COLOR_OUTLINE)
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply {
            marginStart = dp(18)
            marginEnd = dp(18)
        }
    }

    private fun statusChip(): TextView = TextView(this).apply {
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        minWidth = dp(58)
    }

    private fun setChip(view: TextView, positive: Boolean, label: String) {
        view.text = label
        if (positive) {
            view.setTextColor(COLOR_SUCCESS)
            view.background = rounded(COLOR_SUCCESS_CONTAINER, 14f)
        } else {
            view.setTextColor(COLOR_WARNING)
            view.background = rounded(COLOR_WARNING_CONTAINER, 14f)
        }
    }

    private fun stepButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 26f
        setTextColor(COLOR_PRIMARY)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = rippleRounded(COLOR_PRIMARY_CONTAINER, 20f)
        setOnClickListener { onClick() }
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            styleActionButton(this, primary, destructive = false)
        }
    }

    private fun styleActionButton(view: TextView, primary: Boolean, destructive: Boolean) {
        when {
            destructive -> {
                view.setTextColor(COLOR_ERROR)
                view.background = rippleRounded(COLOR_ERROR_CONTAINER, 26f, COLOR_ERROR)
            }
            primary -> {
                view.setTextColor(COLOR_ON_PRIMARY)
                view.background = rippleRounded(COLOR_PRIMARY, 26f)
            }
            else -> {
                view.setTextColor(COLOR_PRIMARY)
                view.background = rippleRounded(COLOR_SURFACE, 26f, COLOR_OUTLINE)
            }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        style: Int = Typeface.NORMAL
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setTypeface(Typeface.DEFAULT, style)
        includeFontPadding = false
        setLineSpacing(0f, 1.08f)
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun rippleRounded(fill: Int, radiusDp: Float, stroke: Int? = null): RippleDrawable {
        val content = rounded(fill, radiusDp, stroke)
        return RippleDrawable(ColorStateList.valueOf(0x22000000), content, null)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun space(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(heightDp)
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
