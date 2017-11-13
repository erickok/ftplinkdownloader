package nl.nl2312.ftplinkdownloader.ui

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker.PERMISSION_GRANTED
import android.view.View
import android.widget.EditText
import android.widget.Toast
import nl.nl2312.ftplinkdownloader.R
import nl.nl2312.ftplinkdownloader.service.DownloadService

class DownloadActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, WRITE_EXTERNAL_STORAGE)) {
                showPermissionRationale()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_STORAGE)
            }
            return
        }

        // Redirect FTP url download request to download service
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

    fun onStartDownload(@Suppress("UNUSED_PARAMETER") view: View) {
        val username = (findViewById<EditText>(R.id.username_edit) as EditText).text
        val password = (findViewById<EditText>(R.id.password_edit) as EditText).text

        startService(Intent(this, DownloadService::class.java)
                .setDataAndType(intent?.data, intent?.type)
                .putExtra(DownloadService.EXTRA_USERNAME, username.toString())
                .putExtra(DownloadService.EXTRA_PASSWORD, password.toString()))
        finish()
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
                .setMessage(R.string.permission_required)
                .setPositiveButton(android.R.string.ok, { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_STORAGE)
                })
                .setNegativeButton(android.R.string.cancel, { _, _ ->
                    finish()
                })
                .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (grantResults.contains(PERMISSION_GRANTED)) {
                // Start download now that we have the permission, using the original intent (with data uri)
                startService(Intent(this, DownloadService::class.java)
                        .setDataAndType(intent?.data, intent?.type))
                finish()
            } else {
                // Still no permission
                Toast.makeText(this, R.string.permission_missing, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {

        const val EXTRA_REQUEST_AUTHENTICATION = "request_authentication"
        private const val REQUEST_PERMISSION_STORAGE = 1

    }

}
