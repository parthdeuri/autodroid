package com.iitg.autologin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Switch
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var store: CredentialStore
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvSub: TextView
    private lateinit var ssidList: LinearLayout
    private lateinit var btnAdd: Button
    private lateinit var rowPerms: TextView
    private lateinit var swEnabled: Switch
    private lateinit var tvRecent: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 3000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun small(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.GRAY)
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun row(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, dp(14), 0, dp(14))
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CredentialStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "IITG Autologin"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        swEnabled = Switch(this).apply { isChecked = store.enabled }
        swEnabled.setOnCheckedChangeListener { _, on ->
            if (store.enabled != on) {
                store.enabled = on
                LogStore.log("UI", "app switched ${if (on) "ON" else "OFF"}")
                if (on) {
                    Scheduler.ensure(this)
                    Scheduler.enqueueNow(this, "switched-on")
                } else {
                    Scheduler.disable(this)
                    store.lastStatus = "Off"
                }
                refresh()
            }
        }
        titleRow.addView(swEnabled)
        root.addView(titleRow)

        tvStatus = TextView(this).apply { textSize = 15f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(12), 0, 0) }
        tvSub = TextView(this).apply { textSize = 12f; setTextColor(Color.GRAY) }
        root.addView(tvStatus)
        root.addView(tvSub)

        root.addView(small("Account"))
        etUser = EditText(this).apply { hint = "Username"; setSingleLine(); setText(store.username) }
        etPass = EditText(this).apply {
            hint = "Password"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(store.password)
        }
        root.addView(etUser)
        val passRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        passRow.addView(etPass, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        passRow.addView(TextView(this).apply {
            text = "Show"
            textSize = 14f
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener {
                val hidden = (etPass.inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD
                etPass.inputType = InputType.TYPE_CLASS_TEXT or
                    if (hidden) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                etPass.setSelection(etPass.text.length)
                text = if (hidden) "Hide" else "Show"
            }
        })
        root.addView(passRow)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val half = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        actions.addView(button("Save") {
            store.username = etUser.text.toString().trim()
            store.password = etPass.text.toString()
            LogStore.log("UI", "credentials saved (user=${store.username})")
            Scheduler.ensure(this)
            toast("Saved")
            refresh()
        }, half)
        actions.addView(button("Login now") {
            LogStore.log("UI", "manual login requested")
            Scheduler.enqueueNow(this, "manual")
            toast("Logging in. Check status in a few seconds.")
        }, half)
        root.addView(actions)

        root.addView(small("Wi-Fi networks"))
        ssidList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ssidList)
        btnAdd = button("") {
            val s = WifiUtil.currentSsid(this)
            if (s == null) {
                toast("Can't read the Wi-Fi name. Fix permissions first.")
                LogStore.log("UI", "add current Wi-Fi failed: SSID unreadable")
            } else {
                store.addSsid(s)
                LogStore.log("UI", "SSID added: $s")
                refresh()
            }
        }
        root.addView(btnAdd)

        root.addView(small("Recent activity"))
        tvRecent = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
        }
        root.addView(tvRecent)

        root.addView(small(""))
        rowPerms = row("") { showPermissions() }
        root.addView(rowPerms)
        root.addView(row("Log  ›") { showLog() })

        Scheduler.ensure(this)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        LogStore.log("UI", "permission result: ${permissions.toList()} -> ${grantResults.toList()}")
        refresh()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun batteryOk() =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun refresh() {
        val last = store.lastLoginAt
        if (swEnabled.isChecked != store.enabled) swEnabled.isChecked = store.enabled
        tvStatus.text = store.lastStatus
        tvRecent.text = LogStore.read(6000).trim().lines().takeLast(12).joinToString("\n")
        tvSub.text = if (last == 0L) "No login yet"
        else "Last login ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last))}"

        ssidList.removeAllViews()
        val ssids = store.ssids.sorted()
        if (ssids.isEmpty()) ssidList.addView(TextView(this).apply { text = "None yet. Add at least one."; setTextColor(Color.GRAY) })
        for (s in ssids) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            r.addView(TextView(this).apply {
                text = s
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(TextView(this).apply {
                text = "✕"
                textSize = 18f
                setPadding(dp(16), dp(4), dp(4), dp(4))
                setOnClickListener {
                    store.removeSsid(s)
                    LogStore.log("UI", "SSID removed: $s")
                    refresh()
                }
            })
            ssidList.addView(r)
        }
        val cur = WifiUtil.currentSsid(this)
        btnAdd.text = if (cur != null) "+ Add current Wi-Fi ($cur)" else "+ Add current Wi-Fi"
        btnAdd.isAllCaps = false

        val todo = listOf(WifiUtil.hasFineLocation(this), WifiUtil.hasBackgroundLocation(this), batteryOk()).count { !it }
        rowPerms.text = if (todo == 0) "Permissions: all set  ›" else "Permissions: $todo to fix  ›"
    }

    private fun showPermissions() {
        val items = arrayOf(
            "${if (WifiUtil.hasFineLocation(this)) "✓" else "✗"} Location (reads the Wi-Fi name)",
            "${if (WifiUtil.hasBackgroundLocation(this)) "✓" else "✗"} Location all the time",
            "${if (batteryOk()) "✓" else "✗"} Battery unrestricted"
        )
        AlertDialog.Builder(this).setTitle("Permissions").setItems(items) { _, which ->
            when (which) {
                0 -> requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                1 -> {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    toast("Permissions > Location > Allow all the time")
                }
                2 -> try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    LogStore.log("UI", "battery exemption intent failed", e)
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun showLog() {
        val tv = TextView(this).apply {
            text = LogStore.read()
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Log")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Share") { _, _ -> shareLog() }
            .setNeutralButton("Clear") { _, _ -> LogStore.clear() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun shareLog() {
        val f = LogStore.file()
        if (f == null || !f.exists()) {
            toast("Log is empty")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share log"))
    }
}
