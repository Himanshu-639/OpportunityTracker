package com.opportunitytracker.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.opportunitytracker.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            "com.opportunitytracker.app.ACTION_TRIGGER_ALARM" -> {
                val oppId = intent.getLongExtra(AlarmScheduler.EXTRA_OPPORTUNITY_ID, -1L)
                if (oppId == -1L) return

                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val opportunity = db.opportunityDao().getOpportunityById(oppId)

                    val serviceIntent = Intent(context, AlarmService::class.java).apply {
                        this.action = AlarmService.ACTION_START_ALARM
                        putExtra(AlarmService.EXTRA_OPP_ID, oppId)
                        putExtra(AlarmService.EXTRA_OPP_TITLE, opportunity?.title ?: "Opportunity Deadline!")
                        putExtra(AlarmService.EXTRA_OPP_ORG, opportunity?.organization ?: "")
                        putExtra(AlarmService.EXTRA_FORM_URL, opportunity?.formUrl ?: "")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    // Also launch Full Screen Activity
                    val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                        putExtra(AlarmScheduler.EXTRA_OPPORTUNITY_ID, oppId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(fullScreenIntent)
                }
            }

            "com.opportunitytracker.app.ACTION_DISMISS_ALARM" -> {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    this.action = AlarmService.ACTION_STOP_ALARM
                }
                context.startService(serviceIntent)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Reschedule all upcoming active opportunities after reboot
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = AlarmScheduler(context)
                    val activeList = db.opportunityDao().getActiveUpcomingOpportunities(System.currentTimeMillis())
                    for (opp in activeList) {
                        scheduler.scheduleAlarmsForOpportunity(opp)
                    }

                    // Background sync with connected Google Calendar if configured
                    try {
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val calId = prefs.getLong("selected_calendar_id", -1L)
                        val calManager = com.opportunitytracker.app.data.calendar.CalendarContractManager(context)
                        if (calId != -1L && calManager.hasCalendarPermission()) {
                            com.opportunitytracker.app.data.repository.OpportunityRepository(context).syncAllWithGoogleCalendar(calId)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
