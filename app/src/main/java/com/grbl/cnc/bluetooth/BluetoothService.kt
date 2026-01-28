package com.grbl.cnc.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.grbl.cnc.grbl.GrblStatus
import com.grbl.cnc.grbl.GrblStatusParser
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val context: Context) {


    companion object {
        val GRBL_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Volatile
    var isConnected = false
        private set
    var connectedDeviceName: String? = null
        private set

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    /** STATUS <Idle|MPos|FS> */
    var onStatus: ((GrblStatus) -> Unit)? = null

    //var onMessage: ((String) -> Unit)? = null
    //var onRawMessage: ((String) -> Unit)? = null
    var onOkReceived: (() -> Unit)? = null

    private val rawListeners = mutableListOf<(String) -> Unit>()
    //private val msgListeners = mutableListOf<(String) -> Unit>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun connect(device: BluetoothDevice) {
        connectedDeviceName = device.name ?: "Unknown"
        Thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(GRBL_UUID)
                socket?.connect()

                input = socket?.inputStream
                output = socket?.outputStream
                isConnected = true

                onConnected?.invoke()
                listen()
            } catch (e: Exception) {
                Log.e("BT", "Connect failed", e)
                disconnect()
            }
        }.start()
    }

    private fun listen() {
        val buffer = ByteArray(1024)

        while (isConnected) {
            try {
                val len = input?.read(buffer) ?: -1
                if (len <= 0) continue

                val data = String(buffer, 0, len)

                // RAW → console
                rawListeners.forEach { it.invoke(data) }

                data.lines().forEach { line ->
                    val text = line.trim()
                    if (text.isEmpty()) return@forEach

                    // OK
                    if (text.equals("ok", true)) {
                        isBusy = false
                        onOkReceived?.invoke()
                        return@forEach
                    }

                    // STATUS
                    GrblStatusParser.parse(text)?.let {
                        onStatus?.invoke(it)
                    }
                }

            } catch (e: Exception) {
                disconnect()
            }
        }
    }


    @Volatile
    var isBusy = false
    fun send(cmd: String) {
        try {
            val data = if (cmd.endsWith("\n")) cmd else "$cmd\n"
            isBusy = true
            output?.write(data.toByteArray(Charsets.US_ASCII))
            output?.flush()   // 🔥 PENTING
            Log.d("BT_SEND", data.replace("\n", "\\n"))
        } catch (e: Exception) {
            disconnect()
        }
    }


    fun sendRealtime(cmd: Byte) {
        try {
            output?.write(byteArrayOf(cmd))
        } catch (e: Exception) {
            disconnect()
        }
    }

    fun disconnect() {
        try {
            isConnected = false
            socket?.close()
            onDisconnected?.invoke()
        } catch (_: Exception) {}
    }

    fun addRawListener(cb: (String) -> Unit) {
        rawListeners.add(cb)
    }

    fun removeRawListener(cb: (String) -> Unit) {
        rawListeners.remove(cb)
    }

    //fun addMessageListener(cb: (String) -> Unit) {
    //    msgListeners.add(cb)
    //}
}
