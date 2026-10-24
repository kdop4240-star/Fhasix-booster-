package com.example.gamebooster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var statsText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val pinger = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    private var pingValue: Int? = null

    override fun onCreate() {
        super.onCreate()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bar, null)
        statsText = overlayView.findViewById(R.id.stats)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable and not touchable: taps go through to the game underneath
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            x = 16
            y = 200
        }

        windowManager.addView(overlayView, params)

        pinger.scheduleAtFixedRate({ pingValue = SystemStats.pingMs() }, 0, 3, TimeUnit.SECONDS)
        handler.post(refresh)
    }

    private val refresh = object : Runnable {
        override fun run() {
            val cpu = SystemStats.cpuPercent()
            val ram = SystemStats.ramPercent(this@OverlayService)
            val temp = SystemStats.tempC()
            val ping = pingValue

            statsText.text = buildString {
                append("CPU  ").append(if (cpu >= 0) "$cpu%" else "--").append('\n')
                append("RAM  ").append(if (ram >= 0) "$ram%" else "--").append('\n')
                append("TEMP ").append(temp?.let { "%.0f°C".format(it) } ?: "--").append('\n')
                append("PING ").append(ping?.let { "$it ms" } ?: "--")
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "game_booster"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Game Booster", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("Game Booster is running")
            .setContentText("Performance overlay is on")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        pinger.shutdownNow()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
