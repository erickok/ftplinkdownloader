package nl.nl2312.ftplinkdownloader.service

import android.app.*
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.support.v4.app.NotificationCompat
import android.support.v4.content.FileProvider
import android.util.Log
import kategory.*
import nl.nl2312.ftplinkdownloader.BuildConfig
import nl.nl2312.ftplinkdownloader.R
import nl.nl2312.ftplinkdownloader.extensions.div
import nl.nl2312.ftplinkdownloader.ui.DownloadActivity
import java.io.File
import java.io.IOException

class DownloadService : IntentService("DownloadService") {

    override fun onHandleIntent(intent: Intent?) {

        val maybeUri: Option<Uri> = Option.fromNullable(intent?.data)
                .filter { it.scheme == "ftp" }
        val mime: String = Option.fromNullable(intent?.type)
                .filter { it.isNotEmpty() }
                .getOrElse { "application/octet-stream" }
        val maybeUsername: Option<String> = Option.fromNullable(intent?.getStringExtra(EXTRA_USERNAME))
        val maybePassword: Option<String> = Option.fromNullable(intent?.getStringExtra(EXTRA_PASSWORD))
        val maybeCredentials = Option.monad().map(maybeUsername, maybePassword, { (username, password) -> Credentials(username, password) }).ev()

        maybeUri.map { Link(it, mime, maybeCredentials) }.fold({
            Log.w(TAG, "No supported URI supplied to download (was $maybeUri))")
        }, { link ->
            showDownloading(link)
            download(link) {
                showResult(link, it)
            }
        })
    }

    private fun download(link: Link, after: (Either<File, DownloadFailure>) -> Unit) {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = path / link.uri.lastPathSegment

        after(Try {
            path.mkdirs()

            // TODO Download file

            //file.outputStream().use {  }
            Thread.sleep(5000)
            throw IOException()
        }.fold({
            when (it) {
                is IOException -> DownloadFailure.AUTHENTICATION_FAILED.right()
                else -> DownloadFailure.IO_EXCEPTION.right()
            }
        }, {
            // TODO? MediaScannerConnection.scanFile?
            file.left()
        }))
    }

    private fun showDownloading(link: Link) {
        val title = getString(R.string.status_downloading, link.uri.lastPathSegment)
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_downloading)
                .setContentTitle(title)
                .setContentText(link.uri.toString())
                .setTicker(title)
                .build())
    }

    private fun showResult(link: Link, result: Either<File, DownloadFailure>) {
        stopForeground(true)
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_stat_downloading)
        result.fold({ outputFile ->
            // Success: allow opening of the file
            notify(builder
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentTitle(getString(R.string.status_ready, link.uri.lastPathSegment))
                    .setContentIntent(PendingIntent.getActivity(
                            applicationContext,
                            0,
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(FileProvider.getUriForFile(applicationContext, AUTHORITY, outputFile), link.mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .build())
        }, {
            // Failure: allow applicable retry
            notify(when (it) {
                DownloadFailure.AUTHENTICATION_FAILED -> {
                    builder
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentTitle(getString(R.string.status_needsauth))
                            .setContentText(link.uri.lastPathSegment)
                            .setTicker(getString(R.string.status_needsauth))
                            .setContentIntent(PendingIntent.getActivity(
                                    applicationContext,
                                    0,
                                    Intent(applicationContext, DownloadActivity::class.java)
                                            .setDataAndType(link.uri, link.mime)
                                            .putExtra(DownloadActivity.EXTRA_REQUEST_AUTHENTICATION, true),
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            ))
                            .build()
                }
                else -> {
                    builder
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentTitle(getString(R.string.status_downloadfailed))
                            .setContentText(link.uri.lastPathSegment)
                            .setTicker(getString(R.string.status_downloadfailed))
                            .setContentIntent(PendingIntent.getService(
                                    applicationContext,
                                    0,
                                    Intent(applicationContext, DownloadService::class.java)
                                            .setDataAndType(link.uri, link.mime),
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            ))
                            .build()
                }
            })
        })
    }

    private fun notify(notification: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            ensureNotificationChannel(DownloadService.NOTIFICATION_CHANNEL_ID, getString(R.string.status_downloads))
            notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {

        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"

        private const val NOTIFICATION_CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1000
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider"
    }

}

data class Link(
        val uri: Uri,
        val mime: String,
        val credentials: Option<Credentials>)

data class Credentials(
        val username: String,
        val password: String)

enum class DownloadFailure {
    AUTHENTICATION_FAILED,
    IO_EXCEPTION
}

private fun NotificationManager.ensureNotificationChannel(channelId: String, name: String) {

    val notificationChannel by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
        } else {
            null
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createNotificationChannel(notificationChannel)
    }

}
