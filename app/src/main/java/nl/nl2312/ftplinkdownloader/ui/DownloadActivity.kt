package nl.nl2312.ftplinkdownloader.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import nl.nl2312.ftplinkdownloader.R
import nl.nl2312.ftplinkdownloader.service.DownloadService

class DownloadActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getBooleanExtra(EXTRA_REQUEST_AUTHENTICATION, false) == false) {
            // Start download service
            startService(Intent(this, DownloadService::class.java)
                    .setDataAndType(intent?.data, intent?.type))
            finish()
            return
        }

        // Authentication required: ask for credentials
        setContentView(R.layout.activity_credentials)
    }

    fun onStartDownload(view: View) {
        val username = (findViewById<EditText>(R.id.username_edit) as EditText).text
        val password = (findViewById<EditText>(R.id.password_edit) as EditText).text

        startService(Intent(this, DownloadService::class.java)
                .setDataAndType(intent?.data, intent?.type)
                .putExtra(DownloadService.EXTRA_USERNAME, username)
                .putExtra(DownloadService.EXTRA_PASSWORD, password))
        finish()
    }

    companion object {

        const val EXTRA_REQUEST_AUTHENTICATION = "request_authentication"

    }

}
