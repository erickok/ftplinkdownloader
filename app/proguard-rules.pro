# Ignore warnings thrown by Progaurd's own duplicate inclusion of org.apache.http.legacy.jar
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**

# Ignore warnings on Kotlin' stdlib's and Kategory' dynamic references
-dontwarn kotlin.internal.**
-dontwarn kotlin.jvm.internal.**
-dontwarn kategory.**
