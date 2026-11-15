package com.dk.mitvremotebt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors

/** Phone ko Bluetooth HID (keyboard + remote) bana deta hai */
@SuppressLint("MissingPermission")
class BtRemote(private val ctx: Context, private val onState: (String) -> Unit) {
    private val adapter: BluetoothAdapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var hid: BluetoothHidDevice? = null
    @Volatile var host: BluetoothDevice? = null
    private val exec = Executors.newSingleThreadExecutor()

    // Report 1 = Keyboard, Report 2 = Consumer control (volume, home, back...)
    private val DESC = intArrayOf(
        0x05,0x01,0x09,0x06,0xA1,0x01,0x85,0x01,0x05,0x07,0x19,0xE0,0x29,0xE7,0x15,0x00,0x25,0x01,
        0x75,0x01,0x95,0x08,0x81,0x02,0x95,0x01,0x75,0x08,0x81,0x01,0x95,0x05,0x75,0x01,0x05,0x08,
        0x19,0x01,0x29,0x05,0x91,0x02,0x95,0x01,0x75,0x03,0x91,0x01,0x95,0x06,0x75,0x08,0x15,0x00,
        0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,0x81,0x00,0xC0,
        0x05,0x0C,0x09,0x01,0xA1,0x01,0x85,0x02,0x15,0x00,0x26,0xFF,0x03,0x19,0x00,0x2A,0xFF,0x03,
        0x75,0x10,0x95,0x01,0x81,0x00,0xC0
    ).map { it.toByte() }.toByteArray()

    private val cb = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onState(if (registered) "Ready ✔ — ab TV se pair karo ya 'TV Chuno' dabao" else "Not ready")
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; onState("Connected: ${device.name}") }
                BluetoothProfile.STATE_CONNECTING -> onState("Connecting...")
                BluetoothProfile.STATE_DISCONNECTED -> { if (host == device) host = null; onState("Disconnected") }
            }
        }
    }

    fun start() {
        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings("Mi TV Remote", "Phone remote", "DK",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESC)
                hid?.registerApp(sdp, null, null, exec, cb)
            }
            override fun onServiceDisconnected(profile: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun bonded(): List<BluetoothDevice> = adapter.bondedDevices.toList()
    fun connect(d: BluetoothDevice) { hid?.connect(d) }
    fun stop() { try { hid?.unregisterApp() } catch (_: Exception) {}; adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }

    private fun kbd(mod: Int, key: Int) {
        val d = host ?: run { onState("TV connect nahi hai"); return }
        hid?.sendReport(d, 1, byteArrayOf(mod.toByte(), 0, key.toByte(), 0, 0, 0, 0, 0))
        hid?.sendReport(d, 1, ByteArray(8))
    }
    private fun consumer(usage: Int) {
        val d = host ?: run { onState("TV connect nahi hai"); return }
        hid?.sendReport(d, 2, byteArrayOf((usage and 0xFF).toByte(), (usage shr 8).toByte()))
        hid?.sendReport(d, 2, byteArrayOf(0, 0))
    }

    fun key(k: Key) { if (k.consumer) consumer(k.code) else kbd(0, k.code); Thread.sleep(20) }

    /** Phone keyboard ka text TV me type karo (English letters/numbers) */
    fun type(text: String) {
        for (ch in text) {
            var mod = 0; val c = ch.lowercaseChar()
            if (ch.isUpperCase()) mod = 0x02
            val k = when (c) {
                in 'a'..'z' -> 0x04 + (c - 'a')
                in '1'..'9' -> 0x1E + (c - '1')
                '0' -> 0x27; ' ' -> 0x2C; '.' -> 0x37; ',' -> 0x36; '-' -> 0x2D; '/' -> 0x38; '\n' -> 0x28
                '@' -> { mod = 0x02; 0x1F }
                else -> -1
            }
            if (k >= 0) { kbd(mod, k); Thread.sleep(35) }
        }
    }
}

enum class Key(val code: Int, val consumer: Boolean) {
    UP(0x52, false), DOWN(0x51, false), LEFT(0x50, false), RIGHT(0x4F, false),
    OK(0x28, false), ESC(0x29, false), DEL(0x2A, false), ENTER(0x28, false), MENU(0x65, false),
    HOME(0x223, true), BACK(0x224, true),
    VOL_UP(0xE9, true), VOL_DOWN(0xEA, true), MUTE(0xE2, true), PLAY_PAUSE(0xCD, true),
    POWER(0x30, true), CH_UP(0x9C, true), CH_DOWN(0x9D, true), ASSIST(0x1CB, true), SEARCH(0x221, true)
}
