package nl.nl2312.ftplinkdownloader.extensions

import android.net.Uri

fun Uri.portOrDefault(default: Int) = if (port == -1) default else port

val Uri.isFtps
    get() = scheme == "ftps"

fun Uri.extension(): String {
    val dotIndex = lastPathSegment?.indexOf('.') ?: -1
    return if (dotIndex == -1) lastPathSegment.orEmpty() else lastPathSegment.substring(dotIndex + 1)
}
