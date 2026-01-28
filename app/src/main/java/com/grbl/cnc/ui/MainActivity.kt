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
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grbl.cnc.grbl.GrblState
import com.grbl.cnc.grbl.GrblStatus
import com.grbl.cnc.grbl.GrblStatusParser
import com.grbl.cnc.ui.pager.ConsoleFragment
import java.util.Locale


class MainActivity : AppCompatActivity() {

    @Volatile
    var isJogging = false

    var pendingGrblConnect = false
    private lateinit var txtStatus: TextView
    private lateinit var txtStatusGrbl: TextView
    private lateinit var txtmposX: TextView
    private lateinit var txtmposY: TextView
    private lateinit var txtmposZ: TextView
    private lateinit var txtwposX: TextView
    private lateinit var txtwposY: TextView
    private lateinit var txtwposZ: TextView
    private lateinit var txtFeed: TextView
    private lateinit var txtSpindle: TextView

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    lateinit var btService: BluetoothService
    lateinit var consoleFragment: ConsoleFragment


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
                //handleExit()
            }
        })

        txtStatusGrbl = findViewById(R.id.txtStatusGrbl)

        txtmposX = findViewById(R.id.txtmposX)
        txtmposY = findViewById(R.id.txtmposY)
        txtmposZ = findViewById(R.id.txtmposZ)

        txtwposX = findViewById(R.id.txtwposX)
        txtwposY = findViewById(R.id.txtwposY)
        txtwposZ = findViewById(R.id.txtwposZ)

        txtFeed = findViewById(R.id.txtFeed)
        txtSpindle = findViewById(R.id.txtSpindle)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        viewPager.adapter = MainPagerAdapter(this).also {
            consoleFragment = it.consoleFragment
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Jog"
                1 -> "Probe"
                2 -> "File"
                else -> "Console"
            }
        }.attach()

        btService = BluetoothService(this)

        btService.onConnected = {
            runOnUiThread {
                txtStatusGrbl.text = "Connected"
                pendingGrblConnect = true
            }
            handler.post(statusRunnable)
        }


        btService.onDisconnected = {
            handler.removeCallbacks(statusRunnable)
            runOnUiThread {
                txtStatusGrbl.text = "Disconnected"
            }
        }

        /** 🔥 STATUS SAJA */
        btService.onStatus = { status ->
            runOnUiThread {
                updateStatusUI(status)
            }
        }

        /** 🔥 RAW → CONSOLE */
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
            showBluetoothDialog()
        }
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener{
        }
        findViewById<ImageView>(R.id.btnUnlock).setOnClickListener {
            btService.send("\$X\n")
        }
        findViewById<ImageView>(R.id.btnPower).setOnClickListener {
            if (btService.isConnected){
                btService.sendRealtime(0x18.toByte())
            }
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
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
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

        txtmposX.text = String.format(Locale.US, " %.3f ", s.mposX)
        txtmposY.text = String.format(Locale.US, " %.3f ", s.mposY)
        txtmposZ.text = String.format(Locale.US, " %.3f ", s.mposZ)

        txtwposX.text = String.format(Locale.US, " %.3f ", s.wposX)
        txtwposY.text = String.format(Locale.US, " %.3f ", s.wposY)
        txtwposZ.text = String.format(Locale.US, " %.3f ", s.wposZ)

        txtFeed.text = String.format(Locale.US, "F : %d", s.feed)
        txtSpindle.text = String.format(Locale.US, "S : %d", s.spindle)
    }

    private val statusRunnable = object : Runnable {
        override fun run() {
            if (btService.isConnected && !isJogging && !btService.isBusy) {
                btService.send("?")
            }
            handler.postDelayed(this, 200)
        }
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Yakin ingin keluar dari Dwi Creative CNC?")
            .setPositiveButton("Keluar") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private var currentGrblState = GrblState.UNKNOWN
    fun onGrblStatusUpdate(state: GrblState) {
        currentGrblState = state
    }

    private fun handleExit() {
        if (currentGrblState == GrblState.RUN) {
            showRunningBlockedDialog()
        } else {
            showExitConfirm()
        }
    }

    private fun showExitConfirm() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Mesin dalam keadaan idle.\nKeluar dari Dwi Creative CNC?")
            .setPositiveButton("Keluar") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showRunningBlockedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Mesin Sedang Berjalan")
            .setMessage(
                "CNC masih aktif (${currentGrblState.name}).\n" +
                        "Hentikan proses sebelum keluar."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}