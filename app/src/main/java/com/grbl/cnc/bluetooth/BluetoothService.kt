package com.grbl.cnc.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.grbl.cnc.grbl.GrblStatus
import com.grbl.cnc.grbl.GrblStatusParser
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class BluetoothService(private val context: Context) {
    companion object {
        val GRBL_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val TAG = "BluetoothService"
    }

    @Suppress("DEPRECATION")
    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var rxBuffer = ""

    @Volatile
    var isConnected = false
        private set
    var connectedDeviceName: String? = null
        private set

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    /** STATUS <Idle|MPos|FS> */
    var onStatus: ((GrblStatus) -> Unit)? = null
    var onLine: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onOkReceived: (() -> Unit)? = null
    private val okListeners = CopyOnWriteArrayList<() -> Unit>()
    private val lineListeners = CopyOnWriteArrayList<(String) -> Unit>()

    // CopyOnWriteArrayList: aman dari ConcurrentModificationException
    // jika addRawListener/removeRawListener dipanggil dari thread lain
    // saat listen() sedang iterasi rawListeners
    private val rawListeners = CopyOnWriteArrayList<(String) -> Unit>()

    // =============================
    // PUBLIC API
    // =============================

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
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
                Log.e(TAG, "Connect failed: ${e.message}")
                disconnect()
            }
        }.start()
    }

    fun send(cmd: String) {
        try {
            output?.write(cmd.toByteArray(Charsets.US_ASCII))
            output?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            disconnect()
        }
    }

    fun sendRealtime(cmd: Byte) {
        try {
            output?.write(byteArrayOf(cmd))
            output?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "SendRealtime failed: ${e.message}")
            disconnect()
        }
    }

    fun disconnect() {
        // Set flag dulu agar listen() loop berhenti
        isConnected = false
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        } finally {
            input = null
            output = null
            socket = null
            onDisconnected?.invoke()
        }
    }


    fun addOkListener(cb: () -> Unit) { okListeners.add(cb) }
    fun removeOkListener(cb: () -> Unit) { okListeners.remove(cb) }

    fun addLineListener(cb: (String) -> Unit) { lineListeners.add(cb) }
    fun removeLineListener(cb: (String) -> Unit) { lineListeners.remove(cb) }

    fun addRawListener(cb: (String) -> Unit) { rawListeners.add(cb) }
    fun removeRawListener(cb: (String) -> Unit) { rawListeners.remove(cb) }

    // =============================
    // PRIVATE
    // =============================

    private fun listen() {
        val buffer = ByteArray(1024)

        while (isConnected) {
            try {
                val len = input?.read(buffer) ?: break
                if (len <= 0) continue

                val chunk = String(buffer, 0, len, Charsets.US_ASCII)

                // Kirim raw chunk ke semua listener (misal: ConsoleFragment)
                rawListeners.forEach { it.invoke(chunk) }

                processRx(chunk)

            } catch (e: Exception) {
                if (isConnected) {
                    Log.e(TAG, "Listen error: ${e.message}")
                    disconnect()
                }
                break
            }
        }
    }

    private fun processRx(chunk: String) {
        rxBuffer += chunk

        while (true) {
            val nl = rxBuffer.indexOf('\n')
            if (nl < 0) break

            val line = rxBuffer.substring(0, nl).trim()
            rxBuffer = rxBuffer.substring(nl + 1)

            if (line.isEmpty()) continue

            when {
                // 1️⃣ STATUS REALTIME <...>
                line.startsWith("<") -> {
                    GrblStatusParser.parse(line)?.let {
                        onStatus?.invoke(it)
                    }
                }

                // 2️⃣ OK
                line == "ok" -> {
                    onOkReceived?.invoke()
                }

                // 3️⃣ ERROR
                line.startsWith("error:") -> {
                    onError?.invoke(line)
                    // Tetap panggil onOkReceived agar buffer streaming tidak stuck
                    onOkReceived?.invoke()
                }

                // 4️⃣ LINE RESPONSE ($G, $#, [GC:...], ALARM, dll)
                else -> {
                    onLine?.invoke(line)
                }
            }
        }
    }
}