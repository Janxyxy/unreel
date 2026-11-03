package eu.janxyxy.unreel

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
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
    private lateinit var serviceHint: TextView
    private lateinit var serviceButton: Button
    private lateinit var swEnabled: Switch
    private lateinit var swTab: Switch
    private lateinit var swViewer: Switch
    private lateinit var swDm: Switch
    private lateinit var optionRows: List<View>
    private lateinit var pauseTitle: TextView
    private lateinit var pauseStatus: TextView
    private lateinit var statToday: TextView
    private lateinit var statTotal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val root = findViewById<View>(R.id.root)
        val basePad = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                basePad + insets.systemWindowInsetBottom,
            )
            insets
        }

        serviceStatus = findViewById(R.id.service_status)
        serviceHint = findViewById(R.id.service_hint)
        serviceButton = findViewById(R.id.service_button)
        swEnabled = findViewById(R.id.sw_enabled)
        swTab = findViewById(R.id.sw_tab)
        swViewer = findViewById(R.id.sw_viewer)
        swDm = findViewById(R.id.sw_dm)
        pauseTitle = findViewById(R.id.pause_title)
        pauseStatus = findViewById(R.id.pause_status)
        statToday = findViewById(R.id.stat_today)
        statTotal = findViewById(R.id.stat_total)

        serviceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        toggleRow(R.id.row_enabled, swEnabled) { prefs.enabled = it; refresh() }
        toggleRow(R.id.row_tab, swTab) { prefs.blockTab = it }
        toggleRow(R.id.row_viewer, swViewer) { prefs.blockViewer = it }
        toggleRow(R.id.row_dm, swDm) { prefs.allowDmReels = it }

        findViewById<View>(R.id.row_github).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://" + getString(R.string.github_url))))
        }

        val pauseRow =findViewById<View>(R.id.row_pause)
        pauseRow.setOnClickListener {
            prefs.pausedUntil = if (prefs.isPaused) 0L else System.currentTimeMillis() + PAUSE_MS
            refresh()
        }

        optionRows = listOf(R.id.row_tab, R.id.row_viewer, R.id.row_dm)
            .map { findViewById<View>(it) } + pauseRow
    }

    /** The whole row toggles the switch, not just the switch itself. */
    private fun toggleRow(rowId: Int, sw: Switch, onChange: (Boolean) -> Unit) {
        findViewById<View>(rowId).setOnClickListener { sw.toggle() }
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
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
        serviceStatus.setTextColor(getColor(if (on) R.color.ok else R.color.warn))
        serviceHint.setText(if (on) R.string.service_hint_on else R.string.service_hint_off)
        serviceButton.visibility = if (on) View.GONE else View.VISIBLE

        val enabled = prefs.enabled
        optionRows.forEach { setRowEnabled(it, enabled) }

        if (prefs.isPaused) {
            val until = DateFormat.getTimeFormat(this).format(Date(prefs.pausedUntil))
            pauseTitle.setText(R.string.resume)
            pauseStatus.text = getString(R.string.paused_until, until)
        } else {
            pauseTitle.setText(R.string.pause)
            pauseStatus.setText(R.string.pause_hint)
        }

        statToday.text = prefs.blockedToday.toString()
        statTotal.text = prefs.blockedTotal.toString()
    }

    private fun setRowEnabled(row: View, enabled: Boolean) {
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.4f
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
