package com.dk.mitvremotebt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class BtRemote(private val context: Context) {

    companion object {
        private const val TAG = "BtRemote"

        const val ID_KEYBOARD: Byte = 1
        const val ID_CONSUMER: Byte = 2
        const val ID_MOUSE: Byte = 3

        const val KEY_A: Byte = 0x04
        const val KEY_ENTER: Byte = 0x28
        const val KEY_ESCAPE: Byte = 0x29
        const val KEY_BACKSPACE: Byte = 0x2A
        const val KEY_TAB: Byte = 0x2B
        const val KEY_SPACE: Byte = 0x2C
        const val KEY_RIGHT: Byte = 0x4F
        const val KEY_LEFT: Byte = 0x50
        const val KEY_DOWN: Byte = 0x51
        const val KEY_UP: Byte = 0x52

        const val MOD_LEFT_CTRL: Byte = 0x01
        const val MOD_LEFT_SHIFT: Byte = 0x02

        const val CONSUMER_POWER: Int = 0x0030
        const val CONSUMER_PLAY_PAUSE: Int = 0x00CD
        const val CONSUMER_STOP: Int = 0x00B7
        const val CONSUMER_REWIND: Int = 0x00B4
        const val CONSUMER_FAST_FORWARD: Int = 0x00B3
        const val CONSUMER_MUTE: Int = 0x00E2
        const val CONSUMER_VOL_UP: Int = 0x00E9
        const val CONSUMER_VOL_DOWN: Int = 0x00EA
        const val CONSUMER_CH_UP: Int = 0x009C
        const val CONSUMER_CH_DOWN: Int = 0x009D
        const val CONSUMER_HOME: Int = 0x0223
        const val CONSUMER_BACK: Int = 0x0224
        const val CONSUMER_MENU: Int = 0x0040
        const val CONSUMER_VOICE_SEARCH: Int = 0x0221

        val HID_DESCRIPTOR = byteArrayOf(
            // Report ID 1: Keyboard
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x06.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), ID_KEYBOARD,
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x06.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x65.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x29.toByte(), 0x65.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte(),

            // Report ID 2: Consumer Control
            0x05.toByte(), 0x0C.toByte(),
            0x09.toByte(), 0x01.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), ID_CONSUMER,
            0x19.toByte(), 0x00.toByte(),
            0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x26.toByte(), 0xFF.toByte(), 0x03.toByte(),
            0x75.toByte(), 0x10.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte(),

            // Report ID 3: Relative Mouse
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x02.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), ID_MOUSE,
            0x09.toByte(), 0x01.toByte(),
            0xA1.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x09.toByte(),
            0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x03.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x03.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x05.toByte(),
            0x81.toByte(), 0x01.toByte(),
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x30.toByte(),
            0x09.toByte(), 0x31.toByte(),
            0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x02.toByte(),
            0x81.toByte(), 0x06.toByte(),
            0xC0.toByte(),
            0xC0.toByte()
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var hidDevice: BluetoothHidDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _statusText = MutableStateFlow("Initializing...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                refreshPairedDevices()
                if (pluggedDevice != null) {
                    _connectedDevice.value = pluggedDevice
                    _statusText.value = "Connected"
                } else {
                    _statusText.value = "Tap to Connect"
                }
            } else {
                _statusText.value = "HID Unregistered"
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectedDevice.value = device
                    _statusText.value = "Connected"
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _statusText.value = "Connecting..."
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_connectedDevice.value?.address == device?.address) {
                        _connectedDevice.value = null
                    }
                    _statusText.value = "Disconnected"
                }
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectedDevice.value = null
                _statusText.value = "Service Disconnected"
            }
        }
    }

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _statusText.value = "Turn On Bluetooth"
            return
        }
        refreshPairedDevices()
        bluetoothAdapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping HID", e)
        }
    }

    fun refreshPairedDevices() {
        try {
            _pairedDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get bonded devices", e)
        }
    }

    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Mi TV Smart Remote",
            "Bluetooth Remote & Keyboard",
            "DK",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HID_DESCRIPTOR
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        hidDevice?.registerApp(sdp, null, qos, executor, hidCallback)
    }

    fun connect(device: BluetoothDevice) {
        val hid = hidDevice ?: return
        _statusText.value = "Connecting..."
        hid.connect(device)
    }

    fun disconnect() {
        val dev = _connectedDevice.value ?: return
        hidDevice?.disconnect(dev)
    }

    fun sendKey(keyCode: Byte, modifier: Byte = 0x00) {
        val dev = _connectedDevice.value ?: return
        val hid = hidDevice ?: return
        executor.execute {
            try {
                val down = byteArrayOf(modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00)
                val up = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), down)
                Thread.sleep(15)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), up)
                Thread.sleep(10)
            } catch (e: Exception) {
                Log.e(TAG, "sendKey failed", e)
            }
        }
    }

    fun clearTvInputField() {
        val dev = _connectedDevice.value ?: return
        val hid = hidDevice ?: return
        executor.execute {
            try {
                hid.sendReport(dev, ID_KEYBOARD.toInt(), byteArrayOf(MOD_LEFT_CTRL, 0x00, KEY_A, 0x00, 0x00, 0x00, 0x00, 0x00))
                Thread.sleep(20)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                Thread.sleep(25)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), byteArrayOf(0x00, 0x00, KEY_BACKSPACE, 0x00, 0x00, 0x00, 0x00, 0x00))
                Thread.sleep(20)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            } catch (e: Exception) {
                Log.e(TAG, "clearTvInputField failed", e)
            }
        }
    }

    fun sendConsumer(usageId: Int) {
        val dev = _connectedDevice.value ?: return
        val hid = hidDevice ?: return
        executor.execute {
            try {
                val low = (usageId and 0xFF).toByte()
                val high = ((usageId shr 8) and 0xFF).toByte()
                hid.sendReport(dev, ID_CONSUMER.toInt(), byteArrayOf(low, high))
                Thread.sleep(25)
                hid.sendReport(dev, ID_CONSUMER.toInt(), byteArrayOf(0x00, 0x00))
            } catch (e: Exception) {
                Log.e(TAG, "sendConsumer failed", e)
            }
        }
    }

    fun sendMouse(dx: Int, dy: Int, leftClick: Boolean = false) {
        val dev = _connectedDevice.value ?: return
        val hid = hidDevice ?: return
        val clampedX = dx.coerceIn(-127, 127).toByte()
        val clampedY = dy.coerceIn(-127, 127).toByte()
        val btn: Byte = if (leftClick) 0x01 else 0x00

        executor.execute {
            try {
                hid.sendReport(dev, ID_MOUSE.toInt(), byteArrayOf(btn, clampedX, clampedY))
                if (leftClick) {
                    Thread.sleep(25)
                    hid.sendReport(dev, ID_MOUSE.toInt(), byteArrayOf(0x00, 0x00, 0x00))
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendMouse failed", e)
            }
        }
    }

    fun sendText(text: String, pressEnterAfter: Boolean = false) {
        if (text.isEmpty() && !pressEnterAfter) return
        val dev = _connectedDevice.value ?: return
        val hid = hidDevice ?: return

        executor.execute {
            try {
                for (ch in text) {
                    val mapped = charToHid(ch) ?: continue
                    val (modifier, keyCode) = mapped
                    val down = byteArrayOf(modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00)
                    val up = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), down)
                    Thread.sleep(14)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), up)
                    Thread.sleep(14)
                }
                if (pressEnterAfter) {
                    val down = byteArrayOf(0x00, 0x00, KEY_ENTER, 0x00, 0x00, 0x00, 0x00, 0x00)
                    val up = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), down)
                    Thread.sleep(18)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), up)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendText failed", e)
            }
        }
    }

    private fun charToHid(c: Char): Pair<Byte, Byte>? {
        val shift = MOD_LEFT_SHIFT
        val none: Byte = 0x00
        return when (c) {
            in 'a'..'z' -> Pair(none, (0x04 + (c - 'a')).toByte())
            in 'A'..'Z' -> Pair(shift, (0x04 + (c - 'A')).toByte())
            in '1'..'9' -> Pair(none, (0x1E + (c - '1')).toByte())
            '0' -> Pair(none, 0x27.toByte())
            ' ' -> Pair(none, KEY_SPACE)
            '
' -> Pair(none, KEY_ENTER)
            '!' -> Pair(shift, 0x1E.toByte())
            '@' -> Pair(shift, 0x1F.toByte())
            '#' -> Pair(shift, 0x20.toByte())
            '$' -> Pair(shift, 0x21.toByte())
            '%' -> Pair(shift, 0x22.toByte())
            '^' -> Pair(shift, 0x23.toByte())
            '&' -> Pair(shift, 0x24.toByte())
            '*' -> Pair(shift, 0x25.toByte())
            '(' -> Pair(shift, 0x26.toByte())
            ')' -> Pair(shift, 0x27.toByte())
            '-' -> Pair(none, 0x2D.toByte())
            '_' -> Pair(shift, 0x2D.toByte())
            '=' -> Pair(none, 0x2E.toByte())
            '+' -> Pair(shift, 0x2E.toByte())
            '[' -> Pair(none, 0x2F.toByte())
            '{' -> Pair(shift, 0x2F.toByte())
            ']' -> Pair(none, 0x30.toByte())
            '}' -> Pair(shift, 0x30.toByte())
            '\' -> Pair(none, 0x31.toByte())
            '|' -> Pair(shift, 0x31.toByte())
            ';' -> Pair(none, 0x33.toByte())
            ':' -> Pair(shift, 0x33.toByte())
            ''' -> Pair(none, 0x34.toByte())
            '"' -> Pair(shift, 0x34.toByte())
            '`' -> Pair(none, 0x35.toByte())
            '~' -> Pair(shift, 0x35.toByte())
            ',' -> Pair(none, 0x36.toByte())
            '<' -> Pair(shift, 0x36.toByte())
            '.' -> Pair(none, 0x37.toByte())
            '>' -> Pair(shift, 0x37.toByte())
            '/' -> Pair(none, 0x38.toByte())
            '?' -> Pair(shift, 0x38.toByte())
            else -> null
        }
    }
}
