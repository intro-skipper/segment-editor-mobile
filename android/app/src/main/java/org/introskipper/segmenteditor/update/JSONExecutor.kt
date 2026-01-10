package org.introskipper.segmenteditor.update

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

class JSONExecutor(server: String) {

    var jsonListener: ResultListener? = null

    init {
        retrieveJSON(server)
    }

    @get:Throws(IOException::class)
    private val URL.asConnection get() : HttpsURLConnection {
        return (openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            defaultUseCaches = false
        }
    }

    fun retrieveJSON(server: String) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            try {
                val result = URL(server).readText()
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
                var conn = URL(server).asConnection
                var statusCode = conn.responseCode
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    val address = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(address).asConnection
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
}