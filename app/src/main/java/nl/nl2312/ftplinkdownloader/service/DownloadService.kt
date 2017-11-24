package nl.nl2312.ftplinkdownloader.service

import android.app.*
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.webkit.MimeTypeMap
import kategory.Either
import kategory.left
import kategory.right
import nl.nl2312.ftplinkdownloader.R
import nl.nl2312.ftplinkdownloader.extensions.div
import nl.nl2312.ftplinkdownloader.extensions.extension
import nl.nl2312.ftplinkdownloader.extensions.hasNetworkConnected
import nl.nl2312.ftplinkdownloader.extensions.whenNotNull
import nl.nl2312.ftplinkdownloader.ui.DownloadActivity
import java.util.concurrent.CountDownLatch

class DownloadService : IntentService("DownloadService") {

    override fun onHandleIntent(intent: Intent?) {

        // Determine url, mime type and potentially login credentials
        val maybeUri: Uri? = intent?.data
                // Only FPT and FTPS links supported
                ?.takeIf { it.scheme == "ftp" || it.scheme == "ftps" }
                // File need to be explicitly specified
                ?.takeIf { it.lastPathSegment != null && it.lastPathSegment.isNotEmpty() }
        val mime: String = intent?.type
                ?.takeIf { it.isNotEmpty() }
                // Determine mime type based on url (file) extensions
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(intent?.data?.extension()) ?: "application/octet-stream"

        val maybeIntentCredentials: Credentials? =
                whenNotNull(intent?.getStringExtra(EXTRA_USERNAME), intent?.getStringExtra(EXTRA_PASSWORD)) { username, password ->
                    Credentials(username, password)
                }
        val maybeUriCredentials: Credentials? = maybeUri?.let {
            val uriUserAndPassword = it.encodedUserInfo?.split(":")
            val maybeUriUser: String? = uriUserAndPassword?.getOrNull(0)
            val maybeUriPassword: String? = uriUserAndPassword?.getOrNull(1)
            whenNotNull(maybeUriUser, maybeUriPassword) { username, password ->
                Credentials(username, password)
            }
        }
        val maybeCredentials: Credentials? = maybeIntentCredentials ?: maybeUriCredentials

        // Download, with notification feedback
        maybeUri?.let {
            Link(it, mime, maybeCredentials)
        }?.let { link ->
            showDownloading(link)
            download(link) {
                showResult(link, it)
            }
        } ?: Log.w(TAG, "No supported URI supplied to download (was $maybeUri))")
    }

    private fun download(link: Link, after: (Either<DownloadFailure, Uri?>) -> Unit) {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = path / link.uri.lastPathSegment
        path.mkdirs()

        // Short circuit if no connection is available
        if (!hasNetworkConnected()) {
            after(DownloadFailure.NO_CONNECTION.left())
        }

        // Ensure no file with the given name exists: this overrides any existing file
        if (file.exists()) {
            file.delete()
        }

        // Perform download
        val download = Downloader.retrieve(link, file)

        // Handle download result and return it
        val result = download.fold({
            it.left()
        }, {

            // Add to the Android downloads
            val lock = CountDownLatch(1)
            var scannerUri: Uri? = null
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(link.mime), { _, uri ->
                scannerUri = uri
                lock.countDown()
            })
            lock.await()

            scannerUri.right()
        })

        after(result)
    }

    private fun showDownloading(link: Link) {
        val title = getString(R.string.status_downloading, link.uri.lastPathSegment)
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // TODO Make progress work
                //.setProgress()
                .setSmallIcon(R.drawable.ic_stat_downloading)
                .setContentTitle(title)
                .setContentText(link.uri.toString())
                .setTicker(title)
                // TODO Make cancelling work
                /*.addAction(R.drawable.ic_stat_action_cancel, getString(R.string.status_cancel), PendingIntent.getActivity(
                        applicationContext,
                        0,
                        Intent(applicationContext, DownloadActivity::class.java)
                                .setDataAndType(link.uri, link.mime)
                                .putExtra(DownloadActivity.EXTRA_REQUEST_AUTHENTICATION, true),
                        PendingIntent.FLAG_UPDATE_CURRENT
                ))*/
                .build())
    }

    private fun showResult(link: Link, result: Either<DownloadFailure, Uri?>) {
        stopForeground(true)
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_stat_downloading)
        result.fold({
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
                            .setContentTitle(getString(when (it) {
                                DownloadFailure.NO_CONNECTION -> R.string.status_noconnection
                                DownloadFailure.FILE_NOT_FOUND -> R.string.status_filenotfound
                                DownloadFailure.STORAGE_FULL -> R.string.status_storagefull
                                else -> R.string.status_downloadfailed
                            }))
                            .setContentText(link.uri.lastPathSegment)
                            .setTicker(getString(R.string.status_downloadfailed))
                            .addAction(R.drawable.ic_stat_action_retry, getString(R.string.status_retry), PendingIntent.getService(
                                    applicationContext,
                                    0,
                                    Intent(applicationContext, DownloadService::class.java)
                                            .setDataAndType(link.uri, link.mime),
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            ))
                            .build()
                }
            })
        }, { outputFile ->
            // Success: allow opening of the file
            notify(builder
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentTitle(getString(R.string.status_ready, link.uri.lastPathSegment))
                    .apply {
                        // Adds an Open action if we know the Uri of the file
                        outputFile?.let {
                            addAction(R.drawable.ic_stat_action_open, getString(R.string.status_open), PendingIntent.getActivity(
                                    applicationContext,
                                    0,
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(it, link.mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    PendingIntent.FLAG_UPDATE_CURRENT))
                        }
                    }
                    .build())
        })
    }

    private fun notify(notification: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            ensureNotificationChannel(DownloadService.NOTIFICATION_CHANNEL_ID, getString(R.string.status_downloadschannel))
            notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {

        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"

        private const val NOTIFICATION_CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1000
    }

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
