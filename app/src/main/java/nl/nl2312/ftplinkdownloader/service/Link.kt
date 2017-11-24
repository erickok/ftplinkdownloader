package nl.nl2312.ftplinkdownloader.service

import android.net.Uri

data class Link(
        val uri: Uri,
        val mime: String,
        val credentials: Credentials?)
