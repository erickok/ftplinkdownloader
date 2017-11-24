package nl.nl2312.ftplinkdownloader.extensions

import java.io.File

fun <A, B, R> whenNotNull(a: A?, b: B?, f: (A, B) -> R): R? =
        if (a != null && b != null) f(a, b) else null

operator fun File.div(file: String) = File(this, file)
