package nl.nl2312.ftplinkdownloader.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import nl.nl2312.ftplinkdownloader.R

class HelpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<View>(android.R.id.content).setOnClickListener {
            // ftp://ftp.belnet.be/ubuntu.com/ubuntu/releases/17.10/ubuntu-17.10-beta2-desktop-amd64.iso
            // ftp://ftp.belnet.be/mirror/archlinux.org/lastsync
            // ftp://ftp.belnet.be/mirror/www.tldp.org/du.txt
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("ftp://ftp.belnet.be/mirror/www.tldp.org/du.txt")))
        }
    }

}
