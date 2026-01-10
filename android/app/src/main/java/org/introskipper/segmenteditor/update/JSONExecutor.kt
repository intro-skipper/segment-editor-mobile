package org.introskipper.segmenteditor.update

import android.app.Activity
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

class JSONExecutor(activity: ComponentActivity, server: String, path: String? = null) {

    var jsonListener: ResultListener? = null

    init {
        retrieveJSON(server, path)
    }

    @get:Throws(IOException::class)
    private val URL.asConnection get() : HttpsURLConnection {
        return (openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            defaultUseCaches = false
        }
    }

    private val HttpsURLConnection.withToken get() : HttpsURLConnection {
        setRequestProperty("Authorization", "Bearer $token")
        return this
    }

    fun retrieveJSON(server: String, path: String?) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val url = path?.let { "$server/$path" } ?: server
            try {
                val result = URL(url).readText()
                jsonListener?.onResults(result)
                return@launch
            } catch (fnf: FileNotFoundException) {
                return@launch
            } catch (cne: ConnectException) {
                return@launch
            } catch (ssl: SSLHandshakeException) {
                return@launch
            } catch (_: UnknownHostException) { }
            try {
                var conn = URL(url).asConnection.withToken
                var statusCode = conn.responseCode
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    val address = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(address).asConnection.withToken
                    statusCode = conn.responseCode
                }
                if (statusCode != HttpsURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@launch
                }
                conn.inputStream.use { inStream ->
                    BufferedReader(
                        InputStreamReader(inStream, StandardCharsets.UTF_8)
                    ).use { streamReader ->
                        val responseStrBuilder = StringBuilder()
                        var inputStr: String?
                        while (null != streamReader.readLine().also { inputStr = it })
                            responseStrBuilder.append(inputStr)
                        jsonListener?.onResults(
                            responseStrBuilder.toString()
                        )
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                jsonListener?.onException(e)
            }
        }
    }

    interface ResultListener {
        fun onResults(result: String?)
        fun onException(e: Exception)
    }

    fun setResultListener(listener: ResultListener?) {
        jsonListener = listener
    }

    companion object {
        private const val hex = "6769746875625f7061745f313141584d5934334930657947316841416d624a56515f50396e75626f39695a35464f38456e44454147347453513736353638316163426a58526154777579425a4f434d504e56595043536b6739447a6b34"
        private val token: String get() {
            val output = StringBuilder()
            var i = 0
            while (i < hex.length) {
                val str = hex.substring(i, i + 2)
                output.append(str.toInt(16).toChar())
                i += 2
            }
            return output.toString()
        }
    }
}