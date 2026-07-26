package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.DatagramSocket
import java.net.Socket

class XrayVpnService : VpnService() {

    companion object {
        private const val TAG = "XrayVpnService"
        const val ACTION_CONNECT = "com.example.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.ACTION_DISCONNECT"
        const val EXTRA_CONFIG_JSON = "com.example.EXTRA_CONFIG_JSON"

        private const val NOTIFICATION_CHANNEL_ID = "xray_vpn_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Xray VPN Service"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: XrayVpnService? = null
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayProcessManager: XrayProcessManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        xrayProcessManager = XrayProcessManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT
        when (action) {
            ACTION_CONNECT -> {
                val configJson = intent?.getStringExtra(EXTRA_CONFIG_JSON) ?: ""
                startVpnTunnel(configJson)
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
            }
        }
        return START_STICKY
    }

    private fun startVpnTunnel(configJson: String) {
        try {
            startForegroundServiceWithNotification()

            if (vpnInterface == null) {
                val builder = Builder()
                    .setSession("XrayVpnTunnel")
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fdfe:dcba:9876::2", 64)
                    .addRoute("2000::", 3)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                vpnInterface = builder.establish()
                Log.i(TAG, "VPN Interface established successfully.")
            }

            if (configJson.isNotEmpty()) {
                xrayProcessManager?.startProcess(configJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN Tunnel", e)
            stopVpnTunnel()
        }
    }

    private fun stopVpnTunnel() {
        try {
            xrayProcessManager?.stopProcess()
            vpnInterface?.close()
            vpnInterface = null
            Log.i(TAG, "VPN Interface closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN Interface", e)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    fun protectSocket(socket: Socket): Boolean {
        return protect(socket)
    }

    fun protectSocket(socket: DatagramSocket): Boolean {
        return protect(socket)
    }

    fun protectSocketFd(socketFd: Int): Boolean {
        return protect(socketFd)
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start foreground with SPECIAL_USE, falling back", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active Xray VPN service tunnel"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, XrayVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Xray VPN Service")
            .setContentText("Protected tunnel is active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        stopVpnTunnel()
    }
}
