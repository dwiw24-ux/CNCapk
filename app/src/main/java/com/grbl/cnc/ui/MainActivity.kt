package com.grbl.cnc.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.grbl.cnc.R
import com.grbl.cnc.ui.pager.MainPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.grbl.cnc.bluetooth.BluetoothService
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresPermission
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grbl.cnc.grbl.GrblStatus
import com.grbl.cnc.ui.pager.ConsoleFragment
import com.grbl.cnc.ui.pager.FileFragment
import java.util.Locale
import androidx.activity.viewModels
import com.grbl.cnc.ui.pager.MainViewModel


class MainActivity : AppCompatActivity() {

    @Volatile
    var isJogging = false
    var pendingGrblConnect = false

    private lateinit var txtStatus: TextView
    private lateinit var txtWcs: TextView
    private lateinit var txtStatusGrbl: TextView
    private lateinit var txtmposX: TextView
    private lateinit var txtmposY: TextView
    private lateinit var txtmposZ: TextView
    private lateinit var txtwposX: TextView
    private lateinit var txtwposY: TextView
    private lateinit var txtwposZ: TextView
    private lateinit var txtFeed: TextView
    private lateinit var txtSpindle: TextView
    private lateinit var txtLimX: TextView
    private lateinit var txtLimY: TextView
    private lateinit var txtLimZ: TextView

    private val viewModel: MainViewModel by viewModels()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    lateinit var btService: BluetoothService
    lateinit var consoleFragment: ConsoleFragment

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0,inset.top, 0, inset.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

        txtStatus = findViewById(R.id.txtStatus)
        txtStatusGrbl = findViewById(R.id.txtStatusGrbl)
        txtWcs = findViewById(R.id.txtWcs)

        txtmposX = findViewById(R.id.txtmposX)
        txtmposY = findViewById(R.id.txtmposY)
        txtmposZ = findViewById(R.id.txtmposZ)

        txtwposX = findViewById(R.id.txtwposX)
        txtwposY = findViewById(R.id.txtwposY)
        txtwposZ = findViewById(R.id.txtwposZ)

        txtFeed = findViewById(R.id.txtFeed)
        txtSpindle = findViewById(R.id.txtSpindle)

        txtLimX = findViewById(R.id.txtLimX)
        txtLimY = findViewById(R.id.txtLimY)
        txtLimZ = findViewById(R.id.txtLimZ)

        txtwposX.setOnClickListener { showWPosDialog('X') }
        txtwposY.setOnClickListener { showWPosDialog('Y') }
        txtwposZ.setOnClickListener { showWPosDialog('Z') }

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        viewPager.adapter = MainPagerAdapter(this).also {
            consoleFragment = it.consoleFragment
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "JOG"
                1 -> "PROBE"
                2 -> "FILE"
                3 -> "EDITOR"
                else -> "CONSOLE"
            }
        }.attach()

        btService = BluetoothService(this)

        btService.onConnected = {
            runOnUiThread {
                txtStatus.text = "BT Connected"
                pendingGrblConnect = true
                Toast.makeText(this, "Bluetooth Connected", Toast.LENGTH_SHORT).show()
            }
            handler.post(statusRunnable)
        }

        btService.onDisconnected = {
            handler.removeCallbacks(statusRunnable)
            runOnUiThread {
                txtStatus.text = "BT Disconnected"
            }
        }

        btService.onStatus = { status ->

            runOnUiThread {
                updateStatusUI(status)
                viewModel.updateStatus(status)
            }
        }

        btService.onLine = { line ->
            runOnUiThread { handleGrblLine(line) }
        }

        btService.addRawListener { raw ->
            runOnUiThread {
                consoleFragment.append(raw)
            }
        }

        findViewById<ImageButton>(R.id.btnBluetooth).setOnClickListener {
            if (!checkBluetoothPermission()) {
                requestBluetoothPermission()
                return@setOnClickListener
            }

            if (btService.isConnected) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Disconnect Bluetooth")
                    .setMessage("Putuskan koneksi dari ${btService.connectedDeviceName}?")
                    .setPositiveButton("Disconnect") { _, _ ->
                        btService.disconnect()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } else {
                showBluetoothDialog()
            }
        }

        findViewById<ImageView>(R.id.btnMenu).setOnClickListener{ view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.menu_main, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener{ item ->
                when (item.itemId) {
                    R.id.settings -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.notifications -> {
                        val intent = Intent(this, NotificationsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.about -> {
                        val intent = Intent(this, AboutActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
        findViewById<ImageView>(R.id.btnUnlock).setOnClickListener {
            btService.send("\$X\n")
        }
        findViewById<ImageView>(R.id.btnPower).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("GRBL Soft Reset")
                .setMessage("Lanjutkan ?")
                .setPositiveButton("Ok") { _, _ ->
                    if (btService.isConnected){
                        btService.sendRealtime(0x18.toByte())
                    }
                }
                    .setNegativeButton("Batal", null)
                    .show()
        }

    }

    private fun checkBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                1001
            )
        }
    }

    @RequiresPermission(anyOf = ["android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN"])
    private fun showBluetoothDialog() {
        val devices = btService.getPairedDevices()
        if (devices.isEmpty()) return

        val names = devices.map {
            "${it.name}\n${it.address}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih ESP32 GRBL")
            .setItems(names) { _, which ->
                btService.connect(devices[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            showBluetoothDialog()
        }
    }

    private fun updateStatusUI(s: GrblStatus) {

        txtStatusGrbl.text = "${s.state}"
        txtStatusGrbl.setTextColor(
            when (s.state) {
                "Idle" -> Color.GREEN
                "Run" -> Color.YELLOW
                "Jog" -> Color.YELLOW
                "Home" -> Color.YELLOW
                "Alarm" -> Color.RED
                "Hold:0" -> Color.RED
                else -> 0xFFFFFFFF.toInt()
            }
        )

        txtmposX.text = String.format(Locale.US, " %.3f ", s.mposX)
        txtmposY.text = String.format(Locale.US, " %.3f ", s.mposY)
        txtmposZ.text = String.format(Locale.US, " %.3f ", s.mposZ)

        txtwposX.text = String.format(Locale.US, " %.3f ✎", s.wposX)
        txtwposY.text = String.format(Locale.US, " %.3f ✎", s.wposY)
        txtwposZ.text = String.format(Locale.US, " %.3f ✎", s.wposZ)

        txtFeed.text = String.format(Locale.US, "Feed: %d", s.feed)
        txtSpindle.text = String.format(Locale.US, "Spin: %d", s.spindle)

        // === LIMIT SWITCH ===
        updateLimitUI(s.pin)

    }

    private fun updateLimitUI(pin: String?) {
        val active = pin ?: ""

        setLimitColor(txtLimX, active.contains("X"))
        setLimitColor(txtLimY, active.contains("Y"))
        setLimitColor(txtLimZ, active.contains("Z"))
    }

    private fun setLimitColor(view: TextView, triggered: Boolean) {
        if (triggered) {
            view.setTextColor(Color.RED)
        } else {
            view.setTextColor(Color.GREEN)
        }
    }

    @Volatile
    var isStreaming = false

    private val statusRunnable = object : Runnable {
        override fun run() {
            if (btService.isConnected ) {

                if (!isStreaming) {
                    btService.send("?")
                } else {
                    // saat streaming, polling diperlambat
                    btService.send("?")
                }
            }
            val interval = if (isStreaming) 100L else getUpdateInterval()
            handler.postDelayed(this, interval)
        }
    }

    private fun getUpdateInterval(): Long {
        val prefs = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)

        val value = prefs.getString("pref_update_interval", "100") ?: "100"
        return value.toLong()
    }

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pref_update_interval") {
            handler.removeCallbacks(statusRunnable)
            handler.post(statusRunnable)
        }
    }

    override fun onResume() {
        super.onResume()

        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(preferenceListener)

        if (btService.isConnected) {
            handler.post(statusRunnable)
        }
    }

    override fun onPause() {
        super.onPause()

        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)

        handler.removeCallbacks(statusRunnable)
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Yakin ingin keluar ?")
            .setPositiveButton("Keluar") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun handleGrblLine(line: String) {
        if (!line.startsWith("[G")) return

        val wcs = when {
            line.contains("G54") -> "G54"
            line.contains("G55") -> "G55"
            line.contains("G56") -> "G56"
            line.contains("G57") -> "G57"
            line.contains("G58") -> "G58"
            line.contains("G59") -> "G59"
            else -> "-"
        }
        txtWcs.text = wcs
    }

    @SuppressLint("SetTextI18n")
    fun showWPosDialog(axis: Char) {
        val view = layoutInflater.inflate(R.layout.dialog_set_wpos, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        val txtAxis = view.findViewById<TextView>(R.id.txtAxis) ?: return
        val edtValue = view.findViewById<EditText>(R.id.edtValue) ?: return
        val btnSet = view.findViewById<Button>(R.id.btnSet) ?: return
        val btnCancel = view.findViewById<Button>(R.id.btnCancel) ?: return

        txtAxis.text = "Set WPos $axis"

        btnSet.setOnClickListener {
            if (!btService.isConnected) {
                Toast.makeText(this, "Bluetooth not connected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val value = edtValue.text.toString().trim()
            if (value.isEmpty()) return@setOnClickListener

            btService.send("G10 L20 P0 $axis $value\n")
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}