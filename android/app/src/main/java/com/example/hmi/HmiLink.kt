package com.example.hmi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors

data class HmiState(
    val status: String = "未连接",
    val connected: Boolean = false,
    val scanning: Boolean = false,
    val devices: List<BtDevice> = emptyList(),
    val attitudeValid: Boolean = false,
    val roll: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val led: Boolean = false,
    val buzzer: Boolean = false,
)

data class BtDevice(val name: String, val address: String)

/**
 * HC-04 经典蓝牙 SPP。载荷就是 robot/demo/bt_config.h 里的 bt_cmd_t / bt_report_t，小端无填充。
 * 蜂鸣器按住时每 200 ms 重发整份命令。
 */
class HmiLink(private val context: Context) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val writer = Executors.newSingleThreadExecutor()
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private var listener: ((HmiState) -> Unit)? = null
    private var state = HmiState()
    private var socket: BluetoothSocket? = null
    @Volatile
    private var output: OutputStream? = null
    private var readerRunning = false

    private val beat = object : Runnable {
        override fun run() {
            if (state.buzzer && state.connected) {
                sendCmd()
                main.postDelayed(this, 200)
            }
        }
    }

    private val found = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.device() ?: return
                    addDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> publish(state.copy(scanning = false))
            }
        }
    }

    fun setListener(callback: (HmiState) -> Unit) {
        listener = callback
        callback(state)
    }

    fun showStatus(text: String) {
        publish(state.copy(status = text, scanning = false))
    }

    fun close() {
        main.removeCallbacks(beat)
        stopScan()
        disconnect()
        writer.shutdownNow()
        io.shutdownNow()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val radio = adapter
        if (radio == null || !radio.isEnabled) {
            publish(state.copy(status = "请先打开手机蓝牙", scanning = false))
            return
        }
        stopScan()
        val devices = radio.bondedDevices.map { it.toBtDevice() }
        publish(state.copy(status = "正在扫描", scanning = true, devices = devices))
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(found, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(found, filter)
        }
        try {
            radio.startDiscovery()
        } catch (_: SecurityException) {
            stopScan()
            publish(state.copy(status = "没有蓝牙权限", scanning = false))
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            adapter?.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        try {
            context.unregisterReceiver(found)
        } catch (_: IllegalArgumentException) {
        }
        if (state.scanning) {
            publish(state.copy(scanning = false))
        }
    }

    fun connect(device: BtDevice) {
        stopScan()
        disconnect()
        publish(state.copy(status = "正在连接 ${device.name}", connected = false))
        io.execute {
            val radio = adapter
            if (radio == null) {
                publish(state.copy(status = "这台手机没有蓝牙"))
                return@execute
            }
            try {
                @SuppressLint("MissingPermission")
                radio.cancelDiscovery()
                val remote = radio.getRemoteDevice(device.address)
                val link = openSocket(remote)
                link.connect()
                socket = link
                output = link.outputStream
                publish(state.copy(status = "已连接 ${device.name}", connected = true))
                readLoop(link.inputStream)
            } catch (error: IOException) {
                disconnect()
                publish(state.copy(status = "连接失败", connected = false))
            }
        }
    }

    fun disconnect() {
        readerRunning = false
        main.removeCallbacks(beat)
        val link = socket
        socket = null
        output = null
        try {
            link?.close()
        } catch (_: IOException) {
        }
        if (state.connected || state.buzzer) {
            publish(state.copy(status = "未连接", connected = false, buzzer = false, attitudeValid = false))
        }
    }

    fun setBuzzer(on: Boolean) {
        if (!state.connected) {
            return
        }
        main.removeCallbacks(beat)
        publish(state.copy(buzzer = on))
        sendCmd()
        if (on) {
            main.postDelayed(beat, 200)
        }
    }

    fun setLed(on: Boolean) {
        if (!state.connected) {
            return
        }
        publish(state.copy(led = on))
        sendCmd()
    }

    private fun sendCmd() {
        /* 与 bt_cmd_t 成员顺序一致：buzzer, led */
        val payload = byteArrayOf(
            if (state.buzzer) 1 else 0,
            if (state.led) 1 else 0,
        )
        val frame = ByteArray(4 + payload.size)
        frame[0] = SOF.toByte()
        frame[1] = TYPE_CMD.toByte()
        frame[2] = payload.size.toByte()
        payload.copyInto(frame, 3)
        frame[3 + payload.size] = xor(TYPE_CMD, payload).toByte()
        write(frame)
    }

    private fun write(bytes: ByteArray) {
        writer.execute {
            try {
                val out = output ?: return@execute
                out.write(bytes)
                out.flush()
            } catch (_: IOException) {
                main.post { disconnect() }
            }
        }
    }

    private fun readLoop(input: InputStream) {
        readerRunning = true
        val parser = RxParser()
        val buffer = ByteArray(128)
        try {
            while (readerRunning) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                parser.push(buffer, count) { applyTelemetry(it) }
            }
        } catch (_: IOException) {
        }
        main.post {
            if (readerRunning || state.connected) {
                disconnect()
            }
        }
    }

    private fun applyTelemetry(sample: Telemetry) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { applyTelemetry(sample) }
            return
        }
        publish(
            state.copy(
                attitudeValid = sample.valid,
                roll = sample.roll,
                pitch = sample.pitch,
                yaw = sample.yaw,
            )
        )
    }

    private fun xor(type: Int, payload: ByteArray): Int {
        var value = type xor payload.size
        for (byte in payload) {
            value = value xor (byte.toInt() and 0xFF)
        }
        return value and 0xFF
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        return try {
            device.createRfcommSocketToServiceRecord(sppUuid)
        } catch (_: IOException) {
            device.createInsecureRfcommSocketToServiceRecord(sppUuid)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        val item = device.toBtDevice()
        if (state.devices.any { it.address == item.address }) {
            return
        }
        publish(state.copy(devices = state.devices + item))
    }

    private fun publish(next: HmiState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state = next
            listener?.invoke(next)
        } else {
            main.post { publish(next) }
        }
    }

    private fun Intent.device(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toBtDevice(): BtDevice {
        val label = name?.takeIf { it.isNotBlank() } ?: "未命名设备"
        return BtDevice(label, address)
    }
}

private const val SOF = 0xA5
private const val TYPE_REPORT = 0x01
private const val TYPE_CMD = 0x02
private const val REPORT_SIZE = 15

private data class Telemetry(
    val valid: Boolean,
    val roll: Float,
    val pitch: Float,
    val yaw: Float,
    val led: Boolean,
)

/* 与 bt_report_t 成员顺序一致：valid, roll_deg, pitch_deg, yaw_deg, led, buzzer */
private class RxParser {
    private var state = 0
    private var type = 0
    private var length = 0
    private var index = 0
    private var sum = 0
    private val payload = ByteArray(32)

    fun push(data: ByteArray, count: Int, onReport: (Telemetry) -> Unit) {
        for (offset in 0 until count) {
            val byte = data[offset].toInt() and 0xFF
            when (state) {
                0 -> if (byte == SOF) state = 1
                1 -> {
                    type = byte
                    sum = byte
                    state = 2
                }
                2 -> {
                    length = byte
                    sum = sum xor byte
                    index = 0
                    state = when {
                        byte == 0 -> 4
                        byte <= payload.size -> 3
                        else -> 0
                    }
                }
                3 -> {
                    payload[index++] = byte.toByte()
                    sum = sum xor byte
                    if (index >= length) state = 4
                }
                else -> {
                    if (byte == (sum and 0xFF) && type == TYPE_REPORT && length == REPORT_SIZE) {
                        decode(payload)?.let(onReport)
                    }
                    state = 0
                }
            }
        }
    }

    private fun decode(raw: ByteArray): Telemetry? {
        val view = ByteBuffer.wrap(raw, 0, REPORT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val valid = view.get().toInt() and 0xFF
        val roll = view.float
        val pitch = view.float
        val yaw = view.float
        val led = view.get().toInt() and 0xFF
        view.get()
        return Telemetry(valid != 0, roll, pitch, yaw, led != 0)
    }
}
