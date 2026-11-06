package com.moustayt.cloudvideo.generator.data

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.moustayt.cloudvideo.generator.MainActivity
import com.moustayt.cloudvideo.generator.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Polls one generation job in the background (survives leaving the app) and notifies when it is done. */
class TrackJobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val dao = AppDb.get(applicationContext).items()
        var failures = 0
        val deadline = System.currentTimeMillis() + 8 * 60_000L

        while (System.currentTimeMillis() < deadline) {
            val item = dao.get(id) ?: return Result.success()
            if (item.status == Status.DONE || item.status == Status.FAILED) return Result.success()
            try {
                val dto = Backends.serviceFor(applicationContext, item.demo).poll(id)
                failures = 0
                val updated = item.copy(
                    status = dto.status,
                    progress = maxOf(item.progress, dto.progress),
                    resultUrl = dto.resultUrl,
                    error = dto.error,
                    errorCode = dto.code,
                )
                dao.upsert(updated)
                if (dto.status == Status.DONE || dto.status == Status.FAILED) {
                    Notifier.finished(applicationContext, updated)
                    return Result.success()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                val fatal = e is ApiException && (e.code == "UNAUTHORIZED" || e.code == "NOT_FOUND")
                if (failures >= 6 || fatal) {
                    val failed = item.copy(status = Status.FAILED, error = e.message ?: e.toString(), errorCode = (e as? ApiException)?.code)
                    dao.upsert(failed)
                    Notifier.finished(applicationContext, failed)
                    return Result.success()
                }
            }
            delay(3000)
        }
        return Result.retry()
    }

    companion object {
        private const val KEY_ID = "id"

        fun enqueue(context: Context, id: String) {
            val request = OneTimeWorkRequestBuilder<TrackJobWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("track-$id", ExistingWorkPolicy.KEEP, request)
        }
    }
}

object Notifier {
    private const val CHANNEL = "jobs"

    fun finished(context: Context, item: LibraryItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (item.status == Status.DONE) context.getString(R.string.notif_done, item.prompt)
        else context.getString(R.string.notif_failed, item.error ?: "")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7C5CFF.toInt())
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), n)
    }
}
