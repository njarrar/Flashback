package com.njarrar.backthen.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.njarrar.backthen.core.buildDayIndex
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.startLanguage
import com.njarrar.backthen.core.yearsForDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
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
     * Schedules the next daily reminder at the chosen local wall-clock time. External callers
     * (UI, timezone/clock receivers) replace any pending work; [ReminderWorker] chains tomorrow's
     * run using [ExistingWorkPolicy.APPEND_OR_REPLACE] so it never cancels itself mid-execution.
     */
    fun schedule(
        context: Context,
        hour: Int,
        minute: Int,
        skipToday: Boolean = false,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        val delayMs = nextReminderDelayMillis(hour, minute, skipToday = skipToday)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

/** Re-arms the daily reminder immediately when the phone changes timezone or wall-clock time. */
class TimezoneChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_TIMEZONE_CHANGED && action != Intent.ACTION_TIME_SET) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = StateFile(appContext).read()
                if (state.reminder.on) {
                    val alreadyNotifiedToday = state.lastNotifiedEpochDay == LocalDate.now().toEpochDay()
                    Reminders.schedule(
                        appContext,
                        state.reminder.hour,
                        state.reminder.minute,
                        skipToday = alreadyNotifiedToday,
                        policy = ExistingWorkPolicy.REPLACE,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * The reminder's title and text for a day with photos from [years] (newest first), in the
 * app's language. Null when the words are missing, so a raw key never reaches the phone.
 */
fun reminderText(day: MonthDay, years: List<Int>): Pair<String, String>? {
    if (years.isEmpty() || !L.ready) return null
    if (!L.has("reminder_title_years") || !L.has("reminder_body_oldest") || !L.hasPlural("years")) return null
    val title = L.s("reminder_title_years", "day" to day.label(), "years" to L.p("years", years.size))
    val body = L.s("reminder_body_oldest", "year" to years.last())
    return title to body
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val stateFile = StateFile(ctx)
        var state = stateFile.read()
        if (!state.reminder.on) return Result.success()
        var evaluatedToday = false
        try {
            val library = DeviceLibrary(ctx)
            if (!state.onboarded || !state.sources.device || !library.hasAccess()) {
                evaluatedToday = true
                return Result.success()
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                evaluatedToday = true
                return Result.success()
            }

            val currentZone = ZoneId.systemDefault().id
            if (state.indexZone != currentZone) {
                val rebuilt = runCatching { buildDayIndex(library.readAllTimes()) }.getOrNull()
                if (rebuilt != null) {
                    state = stateFile.update { it.copy(deviceIndex = rebuilt, indexZone = currentZone) }
                }
            }

            val todayDate = LocalDate.now()
            val epochDay = todayDate.toEpochDay()
            if (state.lastNotifiedEpochDay == epochDay) {
                evaluatedToday = true
                return Result.success()
            }

            // Hidden days are left out of the index, so they never notify.
            val today = MonthDay.of(todayDate)
            val years = yearsForDay(visibleIndex(state), today, todayDate)
            if (years.isEmpty()) {
                evaluatedToday = true
                return Result.success()
            }

            startLanguage(ctx)
            if (!L.ready) startLanguage(ctx)
            // Never post keys such as "reminder_title_years". Skip the day; the next run tries again.
            val (title, body) = reminderText(today, years) ?: run {
                evaluatedToday = true
                return Result.success()
            }
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
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            val posted = runCatching { NotificationManagerCompat.from(ctx).notify(1, note) }.isSuccess
            if (posted) {
                stateFile.update { it.copy(lastNotifiedEpochDay = epochDay) }
            }
            evaluatedToday = true
            return Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            // Re-arm only when not cancelled/stopped so rescheduling from Settings is never clobbered.
            if (!isStopped && evaluatedToday) {
                val latest = stateFile.read()
                if (latest.reminder.on) {
                    Reminders.schedule(
                        ctx,
                        latest.reminder.hour,
                        latest.reminder.minute,
                        skipToday = true,
                        policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
                    )
                }
            }
        }
    }
}
