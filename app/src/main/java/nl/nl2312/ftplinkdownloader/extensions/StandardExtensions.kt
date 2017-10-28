package nl.nl2312.ftplinkdownloader.extensions

import java.io.File

operator fun File.div(file: String) = File(this, file)
