package nl.nl2312.ftplinkdownloader.extensions

import android.content.Context
import android.net.ConnectivityManager

fun Context.hasNetworkConnected(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetworkInfo
    return activeNetwork != null && activeNetwork.isConnectedOrConnecting
}
