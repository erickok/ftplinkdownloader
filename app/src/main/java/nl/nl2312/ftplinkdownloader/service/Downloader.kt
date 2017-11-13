package nl.nl2312.ftplinkdownloader.service

import android.system.ErrnoException
import kategory.Either
import kategory.left
import kategory.right
import nl.nl2312.ftplinkdownloader.extensions.isFtps
import nl.nl2312.ftplinkdownloader.extensions.portOrDefault
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.UnknownHostException

object Downloader {

    fun retrieve(link: Link, localFile: File): Either<File, DownloadFailure> {

        // Set up an FTP client
        val isFtps = link.uri.isFtps
        val port = link.uri.portOrDefault(21)
        val client: FTPClient = if (isFtps) {
            FTPSClient(false)
        } else {
            FTPClient()
        }

        try {

            // Connect and log in
            client.autodetectUTF8 = true
            client.connect(link.uri.host, port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw IOException("Unexpected reply code ${client.replyCode} on connecting")
            }
            link.credentials.fold({
                if (!client.login("anonymous", "")) {
                    throw AuthenticationException("anonymous")
                }
            }, {
                if (!client.login(it.username, it.password)) {
                    throw AuthenticationException(it.username)
                }
            })

            // Prepare file transfer
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.enterLocalPassiveMode()
            client.isUseEPSVwithIPv4

            // Change to target file directory
            val targetDir = link.uri.path.substring(0, link.uri.path.lastIndexOf('/'))
            if (!client.changeWorkingDirectory(targetDir)) {
                throw FileNotFoundException()
            }

            // Stream file contents
            localFile.outputStream().use { localStream ->
                val remoteStream: InputStream = client.retrieveFileStream(link.uri.lastPathSegment)
                        ?: throw FileNotFoundException()
                remoteStream.use {
                    it.copyTo(localStream)
                }
            }

            // Close
            client.noop()
            client.logout()

            return localFile.left()
        } catch (e: AuthenticationException) {
            return DownloadFailure.AUTHENTICATION_FAILED.right()
        } catch (e: FileNotFoundException) {
            return DownloadFailure.FILE_NOT_FOUND.right()
        } catch (e: UnknownHostException) {
            return DownloadFailure.FILE_NOT_FOUND.right()
        } catch (e: IOException) {
            return if (e.cause is ErrnoException && (e.cause as ErrnoException).errno == 28) {
                DownloadFailure.STORAGE_FULL.right()
            } else {
                DownloadFailure.DOWNLOAD_FAILED.right()
            }
        } catch (e: Exception) {
            return DownloadFailure.DOWNLOAD_FAILED.right()
        } finally {
            if (client.isConnected) {
                client.disconnect()
            }
        }
    }

}

class AuthenticationException(username: String) : Exception("Authentication failed for $username")

enum class DownloadFailure {
    NO_CONNECTION,
    AUTHENTICATION_FAILED,
    FILE_NOT_FOUND,
    STORAGE_FULL,
    DOWNLOAD_FAILED
}
