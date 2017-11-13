package nl.nl2312.ftplinkdownloader.service

import android.net.Uri
import kategory.Option

data class Link(
        val uri: Uri,
        val mime: String,
        val credentials: Option<Credentials>)
