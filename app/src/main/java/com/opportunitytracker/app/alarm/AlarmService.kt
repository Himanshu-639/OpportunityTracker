package com.opportunitytracker.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.opportunitytracker.app.OpportunityApp
import com.opportunitytracker.app.R

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val ACTION_START_ALARM = "com.opportunitytracker.app.START_ALARM"
        const val ACTION_STOP_ALARM = "com.opportunitytracker.app.STOP_ALARM"
        const val EXTRA_OPP_ID = "opp_id"
        const val EXTRA_OPP_TITLE = "opp_title"
        const val EXTRA_OPP_ORG = "opp_org"
        const val EXTRA_FORM_URL = "form_url"
        private const val NOTIFICATION_ID = 9999
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_ALARM) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val oppId = intent?.getLongExtra(EXTRA_OPP_ID, -1L) ?: -1L
        val oppTitle = intent?.getStringExtra(EXTRA_OPP_TITLE) ?: "Opportunity Deadline!"
        val oppOrg = intent?.getStringExtra(EXTRA_OPP_ORG) ?: ""
        val formUrl = intent?.getStringExtra(EXTRA_FORM_URL) ?: ""

        startForeground(NOTIFICATION_ID, buildAlarmNotification(oppId, oppTitle, oppOrg, formUrl))
        startAlarmSoundAndVibration()

        return START_STICKY
    }

    private fun buildAlarmNotification(oppId: Long, title: String, org: String, formUrl: String): Notification {
        val fullScreenIntent = Intent(this, FullScreenAlarmActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_OPPORTUNITY_ID, oppId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            oppId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.opportunitytracker.app.ACTION_DISMISS_ALARM"
            putExtra(AlarmScheduler.EXTRA_OPPORTUNITY_ID, oppId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            oppId.toInt() + 100,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OpportunityApp.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Opportunity Deadline: $title")
            .setContentText(if (org.isNotBlank()) "Host: $org" else "Time to submit your application")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPendingIntent)
            .build()
    }

    private fun startAlarmSoundAndVibration() {
        try {
            val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val customSoundUriStr = prefs.getString("alarm_sound_uri", null)
            val alertUri = if (!customSoundUriStr.isNullOrBlank()) {
                try {
                    android.net.Uri.parse(customSoundUriStr)
                } catch (_: Exception) {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                }
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
