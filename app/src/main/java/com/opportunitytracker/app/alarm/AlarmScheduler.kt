package com.opportunitytracker.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.opportunitytracker.app.data.model.Opportunity

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val EXTRA_OPPORTUNITY_ID = "extra_opportunity_id"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val ALARM_TYPE_PREPARATION = "type_prep_1day"
        const val ALARM_TYPE_DEADLINE = "type_deadline_2hr"

        fun getAlarmRequestCode(opportunityId: Long, index: Int): Int {
            return ((opportunityId % 10000).toInt() * 10) + (index % 10)
        }

        fun getDeadlineRequestCode(opportunityId: Long): Int {
            return ((opportunityId % 10000).toInt() * 10) + 9
        }

        fun getSnoozeRequestCode(opportunityId: Long): Int {
            return 4000 + (opportunityId % 10000).toInt()
        }
    }

    fun scheduleAlarmsForOpportunity(opportunity: Opportunity) {
        val now = System.currentTimeMillis()
        val deadline = opportunity.deadlineMillis

        if (deadline <= now) return

        val alarms = opportunity.getAlarms()

        alarms.forEachIndexed { index, alarmTiming ->
            val triggerTime = alarmTiming.getTriggerTime(deadline)
            if (triggerTime > now) {
                scheduleExactAlarm(
                    opportunityId = opportunity.id,
                    alarmType = alarmTiming.toDisplayString(),
                    triggerTimeMillis = triggerTime,
                    requestCode = getAlarmRequestCode(opportunity.id, index)
                )
            }
        }

        // Exact Deadline Alarm (rings at the exact deadline time if in future)
        if (deadline > now) {
            scheduleExactAlarm(
                opportunityId = opportunity.id,
                alarmType = "Deadline reached!",
                triggerTimeMillis = deadline,
                requestCode = getDeadlineRequestCode(opportunity.id)
            )
        }
    }

    private fun scheduleExactAlarm(opportunityId: Long, alarmType: String, triggerTimeMillis: Long, requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.opportunitytracker.app.ACTION_TRIGGER_ALARM"
            putExtra(EXTRA_OPPORTUNITY_ID, opportunityId)
            putExtra(EXTRA_ALARM_TYPE, alarmType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show intent for AlarmClockInfo
        val showIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            putExtra(EXTRA_OPPORTUNITY_ID, opportunityId)
        }
        val pendingShowIntent = PendingIntent.getActivity(
            context,
            requestCode + 50000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, pendingShowIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelAlarms(opportunityId: Long) {
        // Cancel custom alarms (0..4) and deadline alarm (9)
        for (i in 0..9) {
            cancelAlarm(getAlarmRequestCode(opportunityId, i))
        }
        // Cancel snooze alarm
        cancelAlarm(getSnoozeRequestCode(opportunityId))

        // Cancel legacy request codes
        cancelAlarm(1000 + opportunityId.toInt())
        cancelAlarm(2000 + opportunityId.toInt())
        cancelAlarm(3000 + opportunityId.toInt())
        cancelAlarm(4000 + opportunityId.toInt())
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.opportunitytracker.app.ACTION_TRIGGER_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun snoozeAlarm(opportunityId: Long, minutes: Int = 60) {
        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        scheduleExactAlarm(opportunityId, "type_snooze", triggerTime, getSnoozeRequestCode(opportunityId))
    }

    fun snoozeAlarmAt(opportunityId: Long, triggerTimeMillis: Long) {
        scheduleExactAlarm(opportunityId, "type_snooze", triggerTimeMillis, getSnoozeRequestCode(opportunityId))
    }
}
