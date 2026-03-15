package com.screen.brightness

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var previewButton: TextView

    private val bgColors = intArrayOf(
        0x80333333.toInt(), 0x80000000.toInt(), 0x801565C0.toInt(),
        0x80C62828.toInt(), 0x802E7D32.toInt(), 0x80FF8F00.toInt()
    )

    private val fgColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF5722.toInt(),
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF000000.toInt()
    )

    private val icons = arrayOf("☀", "🔆", "💡", "⚡", "🌟", "○")

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissionsAndStart()
        } else {
            updateStatus(getString(R.string.status_notification_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        actionButton = findViewById(R.id.action_button)
        previewButton = findViewById(R.id.preview_button)

        actionButton.setOnClickListener {
            checkPermissionsAndStart()
        }

        setupSettings()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        when {
            !Settings.canDrawOverlays(this) -> {
                updateStatus(getString(R.string.status_overlay_needed))
                actionButton.text = getString(R.string.btn_grant_overlay)
                actionButton.setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            !Settings.System.canWrite(this) -> {
                updateStatus(getString(R.string.status_write_settings_needed))
                actionButton.text = getString(R.string.btn_grant_settings)
                actionButton.setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED -> {
                updateStatus(getString(R.string.status_notification_needed))
                actionButton.text = getString(R.string.btn_grant_notification)
                actionButton.setOnClickListener {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> {
                if (!isServiceRunning()) {
                    startFloatingService()
                }
                updateStatus(getString(R.string.status_running))
                actionButton.text = getString(R.string.btn_stop)
                actionButton.setOnClickListener {
                    stopService(Intent(this, FloatingButtonService::class.java))
                    Toast.makeText(this, getString(R.string.toast_stopped), Toast.LENGTH_SHORT)
                        .show()
                    updateStatus(getString(R.string.status_stopped))
                    actionButton.text = getString(R.string.btn_start)
                    actionButton.setOnClickListener {
                        checkPermissionsAndStart()
                    }
                }
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingButtonService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        startForegroundService(intent)
    }

    private fun sendRefreshIntent() {
        if (isServiceRunning()) {
            val intent = Intent(this, FloatingButtonService::class.java).apply {
                action = FloatingButtonService.ACTION_REFRESH
            }
            startService(intent)
        }
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    // --- 设置 UI ---

    private fun setupSettings() {
        updatePreview()
        setupColorRow(findViewById(R.id.bg_color_row), bgColors, AppPrefs.getBgColor(this)) { color ->
            AppPrefs.setBgColor(this, color)
            updatePreview()
            sendRefreshIntent()
        }
        setupColorRow(findViewById(R.id.fg_color_row), fgColors, AppPrefs.getFgColor(this)) { color ->
            AppPrefs.setFgColor(this, color)
            updatePreview()
            sendRefreshIntent()
        }
        setupIconRow()
    }

    private fun updatePreview() {
        val bgColor = AppPrefs.getBgColor(this)
        val fgColor = AppPrefs.getFgColor(this)
        val icon = AppPrefs.getIcon(this)

        previewButton.text = icon
        previewButton.setTextColor(fgColor)
        previewButton.alpha = 1.0f
        previewButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
    }

    private fun setupColorRow(
        container: LinearLayout,
        colors: IntArray,
        selectedColor: Int,
        onSelect: (Int) -> Unit
    ) {
        container.removeAllViews()
        val circleSize = (36 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()

        for (color in colors) {
            val view = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == selectedColor) {
                        setStroke(
                            (3 * resources.displayMetrics.density).toInt(),
                            ContextCompat.getColor(this@MainActivity, R.color.accent)
                        )
                    }
                }
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                    setMargins(margin, 0, margin, 0)
                }
                setOnClickListener {
                    onSelect(color)
                    setupColorRow(container, colors, color, onSelect)
                }
            }
            container.addView(view)
        }
    }

    private fun setupIconRow() {
        val container = findViewById<LinearLayout>(R.id.icon_row)
        container.removeAllViews()
        val selectedIcon = AppPrefs.getIcon(this)
        val padding = (8 * resources.displayMetrics.density).toInt()

        for (icon in icons) {
            val tv = TextView(this).apply {
                text = icon
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(padding, padding, padding, padding)
                if (icon == selectedIcon) {
                    setBackgroundColor(Color.parseColor("#30000000"))
                }
                setOnClickListener {
                    AppPrefs.setIcon(this@MainActivity, icon)
                    setupIconRow()
                    updatePreview()
                    sendRefreshIntent()
                }
            }
            container.addView(tv)
        }
    }

}
