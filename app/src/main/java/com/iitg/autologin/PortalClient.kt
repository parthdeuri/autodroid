package com.iitg.autologin

import android.net.Network
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** IITG (Fortinet) captive-portal flow: GET login page -> parse magic -> POST credentials. */
object PortalClient {
    private const val TAG = "Portal"
    private const val HOST = "agnigarh.iitg.ac.in"
    private const val BASE = "https://$HOST:1442"
    private const val LOGIN_URL = "$BASE/login?"
    private const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"

    sealed class Result {
        object Success : Result()
        object BadCredentials : Result()
        class Unreachable(val msg: String) : Result()
        class Failed(val msg: String) : Result()
    }

    // Trust-all, but applied ONLY to connections to the portal host (cert may be internal/self-signed).
    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })
    private val sslFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }.socketFactory
    }

    private fun open(network: Network, url: String, method: String): HttpURLConnection {
        val c = network.openConnection(URL(url)) as HttpURLConnection
        c.requestMethod = method
        c.instanceFollowRedirects = false
        c.useCaches = false
        c.setRequestProperty("Connection", "close")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        if (c is HttpsURLConnection && c.url.host == HOST) {
            c.sslSocketFactory = sslFactory
            c.hostnameVerifier = HostnameVerifier { h, _ -> h == HOST }
        }
        return c
    }

    private fun body(c: HttpURLConnection): String {
        val stream = try { c.inputStream } catch (ignored: IOException) { c.errorStream } ?: return ""
        return stream.use { it.readBytes().take(300_000).toByteArray().toString(Charsets.UTF_8) }
    }

    /** HTTP status of generate_204 over [network] (204 = internet works), or -1 on error. */
    fun probe(network: Network): Int {
        return try {
            val c = open(network, PROBE_URL, "GET")
            val code = c.responseCode
            val loc = c.getHeaderField("Location")
            LogStore.log(TAG, "probe generate_204 -> $code${if (loc != null) " Location=$loc" else ""}")
            c.disconnect()
            code
        } catch (e: Exception) {
            LogStore.log(TAG, "probe failed: ${e.javaClass.simpleName}: ${e.message}")
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
        val cookies: String
        val html: String
        try {
            val g = open(network, LOGIN_URL, "GET")
            val code = g.responseCode
            html = body(g)
            cookies = (g.headerFields["Set-Cookie"] ?: emptyList<String>()).joinToString("; ") { it.substringBefore(';') }
            LogStore.log(TAG, "GET login page -> $code, ${html.length} bytes, cookies=${if (cookies.isEmpty()) "none" else "yes"}")
            g.disconnect()
            if (code != 200) {
                return Result.Failed("login page HTTP $code")
            }
        } catch (e: Exception) {
            LogStore.log(TAG, "GET login page failed: ${e.javaClass.simpleName}: ${e.message}")
            return Result.Unreachable("${e.javaClass.simpleName}: ${e.message}")
        }

        // 2. parse tokens
        val magic = attr(html, "magic")
        val redir = attr(html, "4Tredir") ?: LOGIN_URL
        LogStore.log(TAG, "magic=${if (magic == null) "NOT FOUND" else "found(len=${magic.length})"} 4Tredir=$redir")
        if (magic == null) {
            LogStore.log(TAG, "page snippet: " + html.take(300).replace('\n', ' '))
            return Result.Failed("magic token not found (already logged in, or page layout changed)")
        }

        // 3. POST credentials
        try {
            val form = "username=${enc(user)}&password=${enc(pass)}&magic=${enc(magic)}&4Tredir=${enc(redir)}"
            val p = open(network, "$BASE/", "POST")
            p.doOutput = true
            p.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            p.setRequestProperty("Origin", BASE)
            p.setRequestProperty("Referer", LOGIN_URL)
            p.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            if (cookies.isNotEmpty()) p.setRequestProperty("Cookie", cookies)
            p.outputStream.use { it.write(form.toByteArray()) }
            val code = p.responseCode
            val loc = p.getHeaderField("Location")
            val text = body(p)
            p.disconnect()
            LogStore.log(TAG, "POST login -> $code Location=$loc body=${text.length} bytes")

            if (code == 302 || code == 303) return Result.Success
            if (code == 200) {
                val lower = text.lowercase()
                return when {
                    "keepalive" in lower || "logout" in lower -> Result.Success
                    "invalid" in lower || "failed" in lower || "incorrect" in lower -> {
                        LogStore.log(TAG, "failure page snippet: " + text.take(300).replace('\n', ' '))
                        Result.BadCredentials
                    }
                    "login-form" !in lower -> Result.Success
                    else -> Result.Failed("portal returned the login form again")
                }
            }
            return Result.Failed("POST HTTP $code")
        } catch (e: Exception) {
            LogStore.log(TAG, "POST failed: ${e.javaClass.simpleName}: ${e.message}")
            return Result.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
