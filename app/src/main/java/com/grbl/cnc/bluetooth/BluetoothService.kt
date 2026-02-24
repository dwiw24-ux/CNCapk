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

    private var rxBuffer = ""

    companion object {
        val GRBL_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Suppress("DEPRECATION")
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
    var onLine: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onOkReceived: (() -> Unit)? = null

    private val rawListeners = mutableListOf<(String) -> Unit>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_SCAN,"android.permission.BLUETOOTH_CONNECT"])
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

                val chunk = String(buffer, 0, len, Charsets.US_ASCII)

                rawListeners.forEach { it.invoke(chunk) }

                rxBuffer += (chunk)

                while (true) {
                    val nl = rxBuffer.indexOf('\n')
                    if (nl < 0) break

                    val line = rxBuffer.substring(0, nl).trim()
                    rxBuffer = rxBuffer.substring(nl + 1)

                    if (line.isEmpty()) continue

                    when {
                        // 1️⃣ STATUS REALTIME
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
                        }

                        // 4️⃣ LINE RESPONSE ($G, $#, [G54...])
                        else -> {
                            onLine?.invoke(line)
                        }
                    }
                }
            } catch (e: Exception) {
                disconnect()
            }
        }
    }

    fun send(cmd: String) {
        try {
            output?.write(cmd.toByteArray(Charsets.US_ASCII))
            output?.flush()
            //Log.d("BT_SEND", "send: $cmd")
        } catch (e: Exception) {
            disconnect()
        }
    }


    fun sendRealtime(cmd: Byte) {
        try {
            output?.write(byteArrayOf(cmd))
            //Log.d("BT_SEND", "sendRealTime: $cmd")
        } catch (e: Exception) {
            disconnect()
        }
    }

    fun spindleRealtime(cmd: String) {
        try {
            output?.write(cmd.toByteArray(Charsets.US_ASCII))
            output?.flush()
            //Log.d("BT_SEND", "send: $cmd")
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

    private fun processRx(chunk: String) {
        rxBuffer += chunk

        while (true) {
            val nl = rxBuffer.indexOf('\n')
            if (nl < 0) break

            val line = rxBuffer.substring(0, nl).trim()
            rxBuffer = rxBuffer.substring(nl + 1)

            when {
                line == "ok" -> {
                    onOkReceived?.invoke()
                }

                line.startsWith("error:") -> {
                    onOkReceived?.invoke()
                }

                line.startsWith("<") -> {
                    GrblStatusParser.parse(line)?.let {
                        onStatus?.invoke(it)
                    }
                }
            }
        }
    }
}
