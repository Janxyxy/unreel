package eu.janxyxy.unreel

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import java.util.Date

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private lateinit var serviceStatus: TextView
    private lateinit var serviceButton: Button
    private lateinit var swEnabled: Switch
    private lateinit var swTab: Switch
    private lateinit var swViewer: Switch
    private lateinit var swDm: Switch
    private lateinit var pauseButton: Button
    private lateinit var pauseStatus: TextView
    private lateinit var statToday: TextView
    private lateinit var statTotal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val root = findViewById<View>(R.id.root)
        val basePad = root.paddingTop
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                basePad + insets.systemWindowInsetLeft,
                basePad + insets.systemWindowInsetTop,
                basePad + insets.systemWindowInsetRight,
                basePad + insets.systemWindowInsetBottom,
            )
            insets
        }

        serviceStatus = findViewById(R.id.service_status)
        serviceButton = findViewById(R.id.service_button)
        swEnabled = findViewById(R.id.sw_enabled)
        swTab = findViewById(R.id.sw_tab)
        swViewer = findViewById(R.id.sw_viewer)
        swDm = findViewById(R.id.sw_dm)
        pauseButton = findViewById(R.id.pause_button)
        pauseStatus = findViewById(R.id.pause_status)
        statToday = findViewById(R.id.stat_today)
        statTotal = findViewById(R.id.stat_total)

        serviceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        swEnabled.setOnCheckedChangeListener { _, v -> prefs.enabled = v; refresh() }
        swTab.setOnCheckedChangeListener { _, v -> prefs.blockTab = v }
        swViewer.setOnCheckedChangeListener { _, v -> prefs.blockViewer = v }
        swDm.setOnCheckedChangeListener { _, v -> prefs.allowDmReels = v }
        pauseButton.setOnClickListener {
            prefs.pausedUntil = if (prefs.isPaused) 0L else System.currentTimeMillis() + PAUSE_MS
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        swEnabled.isChecked = prefs.enabled
        swTab.isChecked = prefs.blockTab
        swViewer.isChecked = prefs.blockViewer
        swDm.isChecked = prefs.allowDmReels
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun refresh() {
        val on = isServiceEnabled()
        serviceStatus.setText(if (on) R.string.service_on else R.string.service_off)
        serviceStatus.setTextColor(getColor(if (on) R.color.ok else R.color.accent))
        serviceButton.setText(if (on) R.string.service_manage else R.string.service_enable)

        val optionsEnabled = prefs.enabled
        listOf(swTab, swViewer, swDm, pauseButton).forEach { it.isEnabled = optionsEnabled }

        if (prefs.isPaused) {
            val until = DateFormat.getTimeFormat(this).format(Date(prefs.pausedUntil))
            pauseStatus.text = getString(R.string.paused_until, until)
            pauseStatus.visibility = View.VISIBLE
            pauseButton.setText(R.string.resume)
        } else {
            pauseStatus.visibility = View.GONE
            pauseButton.setText(R.string.pause)
        }

        statToday.text = prefs.blockedToday.toString()
        statTotal.text = prefs.blockedTotal.toString()
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, ReelBlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    companion object {
        private const val PAUSE_MS = 5 * 60 * 1000L
    }
}
