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
    private lateinit var lockoutMinutesInput: EditText
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
            "Complete setup, choose a viewing limit and break countdown, then start monitoring.",
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
            subtitle = "Lets the timer detect when YouTube is in use",
            status = usageStatus,
            onClick = {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        ))
        setupCard.addView(divider())
        setupCard.addView(settingRow(
            title = "Display over apps",
            subtitle = "Shows the shutdown screen when time is up",
            status = overlayStatus,
            onClick = {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        ))
        setupCard.addView(divider())
        setupCard.addView(settingRow(
            title = "Parent PIN",
            subtitle = "Allows the parent to end the shutdown countdown early",
            status = pinStatus,
            onClick = { showSetPinDialog() }
        ))
        content.addView(setupCard, matchWidth())

        content.addView(sectionTitle("Time limit", "Continuous YouTube viewing"))
        val timeCard = card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        timeCard.addView(text("Viewing limit", 16f, COLOR_TEXT, Typeface.BOLD))
        timeCard.addView(text(
            "Once YouTube starts the timer, it keeps counting even if YouTube is minimized or the screen turns off.",
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

        content.addView(sectionTitle("Break countdown", "Required break after time is up"))
        val lockoutCard = card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        lockoutCard.addView(text("Countdown duration", 16f, COLOR_TEXT, Typeface.BOLD))
        lockoutCard.addView(text(
            "After the 15-second shutdown animation, this countdown is shown before YouTube becomes available again.",
            14f,
            COLOR_TEXT_SECONDARY
        ).apply { setPadding(0, dp(4), 0, dp(16)) })

        val lockoutStepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lockoutMinus = stepButton("−") { changeLockoutMinutes(-1) }
        val lockoutPlus = stepButton("+") { changeLockoutMinutes(1) }
        lockoutStepper.addView(lockoutMinus, LinearLayout.LayoutParams(dp(48), dp(48)))

        val lockoutValueBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(COLOR_SURFACE_VARIANT, 18f)
            setPadding(dp(10), 0, dp(10), 0)
        }
        lockoutMinutesInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(Prefs.lockoutMinutes(this@MainActivity).toString())
            gravity = Gravity.CENTER
            textSize = 26f
            setTextColor(COLOR_TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = null
            setSelectAllOnFocus(true)
            setPadding(0, 0, 0, 0)
        }
        lockoutValueBox.addView(lockoutMinutesInput, LinearLayout.LayoutParams(dp(70), dp(56)))
        lockoutValueBox.addView(text("min", 14f, COLOR_TEXT_SECONDARY))
        lockoutStepper.addView(lockoutValueBox, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        })
        lockoutStepper.addView(lockoutPlus, LinearLayout.LayoutParams(dp(48), dp(48)))
        lockoutCard.addView(lockoutStepper, matchWidth())
        content.addView(lockoutCard, matchWidth())

        content.addView(space(20))
        monitorButton = actionButton("Start monitoring", primary = true) { toggleMonitoring() }
        content.addView(monitorButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ))

        content.addView(space(10))
        content.addView(actionButton("Test shutdown screen", primary = false) {
            testShutdown()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))

        content.addView(sectionTitle("Parent access", "How to recover the phone"))
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
        parentText.addView(text("Secret parent unlock", 15f, COLOR_TEXT, Typeface.BOLD))
        parentText.addView(text(
            "Tap the top-left corner 5 times quickly, then enter the parent PIN.",
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
        content.addView(text("YouTube Timer Guard · v0.1.7", 12f, COLOR_TEXT_SECONDARY).apply {
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
            setImageResource(R.drawable.kid_icon)
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
        copy.addView(text("YouTube Timer Guard", 24f, COLOR_TEXT, Typeface.BOLD))
        copy.addView(text(
            "Simple, predictable screen-time control",
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
            Prefs.clearLockout(this)
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
        val lockoutMinutes = lockoutMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 15
        minutesInput.setText(minutes.toString())
        lockoutMinutesInput.setText(lockoutMinutes.toString())
        Prefs.setLimitMinutes(this, minutes)
        Prefs.setLockoutMinutes(this, lockoutMinutes)
        Prefs.clearLockout(this)
        Prefs.setEnabled(this, true)
        startForegroundService(Intent(this, MonitorService::class.java))
        refreshStatus()
    }

    private fun testShutdown() {
        if (!Prefs.hasPin(this)) {
            toast("Set a parent PIN first")
            return
        }
        val lockoutMinutes = lockoutMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: 15
        lockoutMinutesInput.setText(lockoutMinutes.toString())
        Prefs.setLockoutMinutes(this, lockoutMinutes)
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

    private fun changeLockoutMinutes(delta: Int) {
        val current = lockoutMinutesInput.text.toString().toIntOrNull() ?: Prefs.lockoutMinutes(this)
        val next = (current + delta).coerceIn(1, 180)
        lockoutMinutesInput.setText(next.toString())
        lockoutMinutesInput.setSelection(lockoutMinutesInput.text.length)
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
            monitoringTitle.text = "Shutdown screen active"
            monitoringTitle.setTextColor(COLOR_ERROR)
            monitoringDescription.text = "Use the secret parent unlock gesture and PIN to restore access."
            monitoringDescription.setTextColor(COLOR_ERROR)
        } else if (enabled) {
            monitoringCard.background = rounded(COLOR_PRIMARY_CONTAINER, 22f)
            monitoringTitle.text = "Monitoring is on"
            monitoringTitle.setTextColor(COLOR_PRIMARY)
            monitoringDescription.text = "YouTube is limited to ${Prefs.limitMinutes(this)} minutes of continuous viewing."
            monitoringDescription.setTextColor(COLOR_TEXT_SECONDARY)
        } else {
            monitoringCard.background = rounded(COLOR_SURFACE_VARIANT, 22f)
            monitoringTitle.text = "Monitoring is off"
            monitoringTitle.setTextColor(COLOR_TEXT)
            monitoringDescription.text = if (usageGranted && overlayGranted && pinSet) {
                "Everything is ready. Choose a time limit and start monitoring."
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
