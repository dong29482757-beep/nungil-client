package com.example.myapplication

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ScheduleManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val CHANNEL_ID = "schedule_channel"
        const val NOTIFICATION_ID_BASE = 2000

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "보호자 등록 일정 알림" }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    /**
     * DB에서 오늘 일정을 백그라운드로 가져와 AlarmManager에 등록.
     * @param userId  USER.id
     * @param userIdx USER.idx
     */
    fun syncSchedulesFromDB(userId: String, userIdx: Int) {
        Thread {
            val schedules = ScheduleRepository.fetchTodaySchedules(userId, userIdx)
            schedules.forEach { item ->
                if (item.triggerTimeMillis > System.currentTimeMillis()) {
                    setAlarm(item.scheduleId.toString(), item.title, item.triggerTimeMillis)
                }
            }
        }.start()
    }

    private fun setAlarm(scheduleId: String, title: String, triggerTimeMillis: Long) {
        val pi = makePendingIntent(scheduleId, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pi)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pi)
        }
    }

    private fun makePendingIntent(scheduleId: String, title: String): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_title", title)
        }
        return PendingIntent.getBroadcast(
            context, scheduleId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showNotification(scheduleId: String, title: String) {
        val doIntent = Intent(context, UserChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("schedule_id", scheduleId)
            putExtra("schedule_title", title)
            putExtra("schedule_auto_execute", true)
        }
        val doPi = PendingIntent.getActivity(
            context, scheduleId.hashCode() + 1, doIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅 일정 시간이 됐어요!")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(doPi)
            .addAction(0, "하기", doPi)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_BASE + scheduleId.hashCode(), notification)
    }
}
