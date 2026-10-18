package com.iitg.autologin

import android.net.Network
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * IITG (Fortinet) captive-portal flow: GET login page -> parse magic -> POST credentials.
 * Talks HTTPS over a raw SSLSocket bound to the Wi-Fi network (explicit SNI, HTTP/1.1,
 * Connection: close) so we control exactly what the portal sees and can log every stage.
 */
object PortalClient {
    private const val TAG = "Portal"
    private const val HOST = "agnigarh.iitg.ac.in"
    private const val PORT = 1442
    private const val BASE = "https://$HOST:$PORT"
    private const val LOGIN_URL = "$BASE/login?"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    sealed class Result {
        object Success : Result()
        object BadCredentials : Result()
        class Unreachable(val msg: String) : Result()
        class Failed(val msg: String) : Result()
    }

    private class Resp(val code: Int, val headers: Map<String, List<String>>, val body: String)

    private class StageException(val stage: String, cause: Throwable) :
        Exception("$stage: ${cause.javaClass.simpleName}: ${cause.message}", cause)

    // Trust-all, used ONLY for sockets to the portal host (cert may be internal/self-signed).
    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })
    private val sslFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }.socketFactory
    }

    private fun request(
        network: Network,
        method: String,
        path: String,
        extra: Map<String, String> = emptyMap(),
        payload: ByteArray? = null
    ): Resp {
        var stage = "dns"
        try {
            val addr = network.getAllByName(HOST).first()
            LogStore.log(TAG, "resolved $HOST -> ${addr.hostAddress}")

            stage = "tcp connect"
            val raw = network.socketFactory.createSocket()
            raw.connect(InetSocketAddress(addr, PORT), 8000)
            raw.soTimeout = 8000

            stage = "tls handshake"
            val ssl = sslFactory.createSocket(raw, HOST, PORT, true) as SSLSocket
            ssl.sslParameters = ssl.sslParameters.apply { serverNames = listOf(SNIHostName(HOST)) }
            return ssl.use {
                it.startHandshake()
                LogStore.log(TAG, "TLS ok: ${it.session.protocol} ${it.session.cipherSuite}")

                stage = "send request"
                val sb = StringBuilder()
                sb.append("$method $path HTTP/1.1\r\n")
                sb.append("Host: $HOST:$PORT\r\n")
                sb.append("User-Agent: $UA\r\n")
                sb.append("Accept: text/html,application/xhtml+xml,*/*\r\n")
                sb.append("Connection: close\r\n")
                extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
                if (payload != null) sb.append("Content-Length: ${payload.size}\r\n")
                sb.append("\r\n")
                it.outputStream.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
                if (payload != null) it.outputStream.write(payload)
                it.outputStream.flush()

                stage = "read response"
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(8192)
                try {
                    while (buf.size() < 400_000) {
                        val n = it.inputStream.read(tmp)
                        if (n < 0) break
                        buf.write(tmp, 0, n)
                    }
                } catch (e: SocketTimeoutException) {
                    if (buf.size() == 0) throw e
                    LogStore.log(TAG, "read timed out after ${buf.size()} bytes; using what we have")
                }
                if (buf.size() == 0) throw java.io.IOException("server closed connection without any response")

                stage = "parse response"
                parse(buf.toByteArray())
            }
        } catch (e: Exception) {
            throw StageException(stage, e)
        }
    }

    private fun parse(data: ByteArray): Resp {
        val text = String(data, Charsets.ISO_8859_1)
        val split = text.indexOf("\r\n\r\n")
        val head = if (split >= 0) text.substring(0, split) else text
        var bodyBytes = if (split >= 0) data.copyOfRange(split + 4, data.size) else ByteArray(0)
        val lines = head.split("\r\n")
        LogStore.log(TAG, "response line: ${lines.first()}")
        val code = lines.first().split(" ").getOrNull(1)?.toIntOrNull() ?: -1
        val headers = HashMap<String, MutableList<String>>()
        for (l in lines.drop(1)) {
            val i = l.indexOf(':')
            if (i > 0) headers.getOrPut(l.substring(0, i).trim().lowercase()) { mutableListOf() }
                .add(l.substring(i + 1).trim())
        }
        if (headers["transfer-encoding"]?.any { it.contains("chunked", true) } == true) {
            bodyBytes = dechunk(bodyBytes)
        }
        return Resp(code, headers, String(bodyBytes, Charsets.UTF_8))
    }

    private fun dechunk(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < b.size) {
            var e = i
            while (e + 1 < b.size && !(b[e] == '\r'.code.toByte() && b[e + 1] == '\n'.code.toByte())) e++
            val size = String(b, i, e - i, Charsets.ISO_8859_1).substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            val start = e + 2
            val end = minOf(start + size, b.size)
            out.write(b, start, end - start)
            i = end + 2
        }
        return out.toByteArray()
    }

    /**
     * Portal session check via keepalive. 200 = logged in, 401 = got the login form (logged out),
     * other = HTTP status, -1 = could not reach the portal.
     */
    fun probe(network: Network): Int {
        return try {
            val r = request(network, "GET", "/keepalive?")
            val loc = r.headers["location"]?.firstOrNull()
            LogStore.log(TAG, "probe keepalive -> ${r.code}${if (loc != null) " Location=$loc" else ""}")
            LogStore.log(TAG, "probe body: ${r.body.take(200).replace("\n", " ")}")
            if (r.code == 200 && r.body.contains("name=\"magic\"")) 401 else r.code
        } catch (e: Exception) {
            LogStore.log(TAG, "probe failed at ${e.message}")
            -1
        }
    }

    private fun attr(html: String, name: String): String? {
        val a = Regex("""name=["']$name["'][^>]*?value=["']([^"']*)["']""").find(html)
        if (a != null) return a.groupValues[1]
        return Regex("""value=["']([^"']*)["'][^>]*?name=["']$name["']""").find(html)?.groupValues?.get(1)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun login(network: Network, user: String, pass: String): Result {
        // 1. GET login page
        val page: Resp
        try {
            page = request(network, "GET", "/login?")
            LogStore.log(TAG, "GET login page -> ${page.code}, ${page.body.length} chars")
        } catch (e: Exception) {
            LogStore.log(TAG, "GET login page failed at ${e.message}")
            return Result.Unreachable(e.message ?: "unknown")
        }
        if (page.code != 200) {
            val loc = page.headers["location"]?.firstOrNull()
            LogStore.log(TAG, "unexpected login-page status ${page.code} Location=$loc")
            return Result.Failed("login page HTTP ${page.code}")
        }
        val cookies = (page.headers["set-cookie"] ?: emptyList()).joinToString("; ") { it.substringBefore(';') }

        // 2. parse tokens
        val magic = attr(page.body, "magic")
        val redir = attr(page.body, "4Tredir") ?: LOGIN_URL
        LogStore.log(TAG, "magic=${if (magic == null) "NOT FOUND" else "found(len=${magic.length})"} 4Tredir=$redir")
        if (magic == null) {
            LogStore.log(TAG, "page snippet: " + page.body.take(300).replace('\n', ' '))
            return Result.Failed("magic token not found (already logged in, or page layout changed)")
        }

        // 3. POST credentials
        try {
            val form = "username=${enc(user)}&password=${enc(pass)}&magic=${enc(magic)}&4Tredir=${enc(redir)}"
            val headers = mutableMapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Origin" to BASE,
                "Referer" to LOGIN_URL
            )
            if (cookies.isNotEmpty()) headers["Cookie"] = cookies
            val r = request(network, "POST", "/", headers, form.toByteArray())
            val loc = r.headers["location"]?.firstOrNull()
            LogStore.log(TAG, "POST login -> ${r.code} Location=$loc body=${r.body.length} chars")

            if (r.code == 302 || r.code == 303) return Result.Success
            if (r.code == 200) {
                val lower = r.body.lowercase()
                return when {
                    "keepalive" in lower || "logout" in lower -> Result.Success
                    "invalid" in lower || "failed" in lower || "incorrect" in lower -> {
                        LogStore.log(TAG, "failure page snippet: " + r.body.take(300).replace('\n', ' '))
                        Result.BadCredentials
                    }
                    "login-form" !in lower -> Result.Success
                    else -> Result.Failed("portal returned the login form again")
                }
            }
            return Result.Failed("POST HTTP ${r.code}")
        } catch (e: Exception) {
            LogStore.log(TAG, "POST failed at ${e.message}")
            return Result.Failed(e.message ?: "unknown")
        }
    }
}
