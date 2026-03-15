package com.screen.brightness

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_button_channel"
        private const val NOTIFICATION_ID = 1
        private const val MOVE_THRESHOLD = 10
        const val ACTION_REFRESH = "com.screen.brightness.ACTION_REFRESH"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var screenWidth = 0
    private var screenHeight = 0
    private var currentButtonSize = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BrightnessHelper.init(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        updateScreenSize()
        createFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            refreshButtonAppearance()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) {
            windowManager.removeView(floatingButton)
        }
        if (BrightnessHelper.isMaxBrightness) {
            BrightnessHelper.restoreBrightness(this)
        }
    }

    private fun updateScreenSize() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingButton() {
        val sizeDp = AppPrefs.getSize(this)
        currentButtonSize = (sizeDp * resources.displayMetrics.density).toInt()

        val bgColor = AppPrefs.getBgColor(this)
        val fgColor = AppPrefs.getFgColor(this)
        val alpha = AppPrefs.getAlpha(this)
        val icon = AppPrefs.getIcon(this)
        val posY = AppPrefs.getPosY(this)
        val snapSide = AppPrefs.getSnapSide(this)

        floatingButton = TextView(this).apply {
            text = icon
            textSize = (sizeDp * 0.43f)
            setTextColor(fgColor)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setSize(currentButtonSize, currentButtonSize)
            }
            this.alpha = alpha
        }

        val startX = if (snapSide == 1) screenWidth - currentButtonSize else 0

        layoutParams = WindowManager.LayoutParams(
            currentButtonSize,
            currentButtonSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = posY
        }

        setupTouchListener()
        windowManager.addView(floatingButton, layoutParams)
    }

    private fun refreshButtonAppearance() {
        if (!::floatingButton.isInitialized) return

        val sizeDp = AppPrefs.getSize(this)
        val newButtonSize = (sizeDp * resources.displayMetrics.density).toInt()
        val bgColor = AppPrefs.getBgColor(this)
        val fgColor = AppPrefs.getFgColor(this)
        val alpha = AppPrefs.getAlpha(this)
        val icon = AppPrefs.getIcon(this)

        val button = floatingButton as TextView
        button.text = icon
        button.textSize = (sizeDp * 0.43f)
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
            setSize(newButtonSize, newButtonSize)
        }

        button.alpha = alpha
        button.setTextColor(if (BrightnessHelper.isMaxBrightness) Color.YELLOW else fgColor)

        if (newButtonSize != currentButtonSize) {
            currentButtonSize = newButtonSize
            layoutParams.width = currentButtonSize
            layoutParams.height = currentButtonSize
            clampPosition()
        }

        windowManager.updateViewLayout(floatingButton, layoutParams)
    }

    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        floatingButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > MOVE_THRESHOLD * MOVE_THRESHOLD) {
                        isDragging = true
                    }
                    layoutParams.x = (initialX + dx.toInt())
                        .coerceIn(0, screenWidth - currentButtonSize)
                    layoutParams.y = (initialY + dy.toInt())
                        .coerceIn(0, screenHeight - currentButtonSize)
                    windowManager.updateViewLayout(floatingButton, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onButtonClick()
                    } else {
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPosition() {
        layoutParams.x = layoutParams.x.coerceIn(0, screenWidth - currentButtonSize)
        layoutParams.y = layoutParams.y.coerceIn(0, screenHeight - currentButtonSize)
    }

    private fun snapToEdge() {
        val centerX = layoutParams.x + currentButtonSize / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - currentButtonSize
        val snapSide = if (targetX == 0) 0 else 1

        val animator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                layoutParams.x = animation.animatedValue as Int
                if (::floatingButton.isInitialized) {
                    windowManager.updateViewLayout(floatingButton, layoutParams)
                }
            }
        }
        animator.start()

        AppPrefs.setPosY(this, layoutParams.y)
        AppPrefs.setSnapSide(this, snapSide)
    }

    private fun onButtonClick() {
        BrightnessHelper.toggleBrightness(this)
        updateButtonAppearance()
    }

    private fun updateButtonAppearance() {
        val button = floatingButton as TextView
        button.alpha = AppPrefs.getAlpha(this)
        button.setTextColor(if (BrightnessHelper.isMaxBrightness) Color.YELLOW else AppPrefs.getFgColor(this))
    }
}
