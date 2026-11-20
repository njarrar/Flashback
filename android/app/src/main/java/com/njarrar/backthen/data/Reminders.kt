package com.njarrar.backthen.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.njarrar.backthen.MainActivity
import com.njarrar.backthen.R
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.startLanguage
import com.njarrar.backthen.core.yearsForDay
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val WORK = "daily-reminder"
private const val CHANNEL = "daily"
const val EXTRA_MONTH = "month"
const val EXTRA_DAY = "day"

/** Computes milliseconds until the next local wall-clock occurrence of [hour]:[minute], handling DST transitions. */
internal fun nextReminderDelayMillis(
    hour: Int,
    minute: Int,
    now: ZonedDateTime = ZonedDateTime.now(),
    skipToday: Boolean = false,
): Long {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    var next = now.toLocalDate().atTime(h, m).atZone(now.zone)
    if (skipToday || !next.isAfter(now)) {
        next = now.toLocalDate().plusDays(1).atTime(h, m).atZone(now.zone)
    }
    return Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
}

object Reminders {
    /**
     * Schedules the next daily reminder at the chosen local wall-clock time. Each run
     * reschedules the following day so DST shifts and timezone travel never drift the clock.
     */
    fun schedule(context: Context, hour: Int, minute: Int, skipToday: Boolean = false) {
        val delayMs = nextReminderDelayMillis(hour, minute, skipToday = skipToday)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val stateFile = StateFile(ctx)
        val state = stateFile.read()
        if (!state.reminder.on) return Result.success()
        try {
            if (!state.onboarded || !state.sources.device || !DeviceLibrary(ctx).hasAccess()) {
                return Result.success()
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()

            val todayDate = LocalDate.now()
            val epochDay = todayDate.toEpochDay()
            if (state.lastNotifiedEpochDay == epochDay) return Result.success()

            // Hidden days are left out of the index, so they never notify.
            val today = MonthDay.of(todayDate)
            val years = yearsForDay(visibleIndex(state), today, todayDate)
            if (years.isEmpty()) return Result.success()

            startLanguage(ctx)
            val manager = ctx.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, L.s("daily_reminder"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_MONTH, today.month)
                .putExtra(EXTRA_DAY, today.day)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val tap = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val note = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFE5533D.toInt())
                .setContentTitle(L.s("reminder_title_years", "day" to today.label(), "years" to L.p("years", years.size)))
                .setContentText(L.s("reminder_body_oldest", "year" to years.last()))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            val posted = runCatching { NotificationManagerCompat.from(ctx).notify(1, note) }.isSuccess
            if (posted) {
                stateFile.update { it.copy(lastNotifiedEpochDay = epochDay) }
            }
            return Result.success()
        } finally {
            // Re-arm for tomorrow's wall-clock time so DST and timezone shifts never accumulate drift.
            val latest = stateFile.read()
            if (latest.reminder.on) {
                Reminders.schedule(ctx, latest.reminder.hour, latest.reminder.minute, skipToday = true)
            }
        }
    }
}
