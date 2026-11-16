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
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class BtRemote(private val context: Context) {

    companion object {
        private const val TAG = "BtRemote"

        // Report IDs
        const val ID_KEYBOARD: Byte = 1
        const val ID_CONSUMER: Byte = 2
        const val ID_MOUSE: Byte = 3

        // Standard USB HID Keyboard Usage IDs (Page 0x07)
        const val KEY_ENTER: Byte = 0x28
        const val KEY_ESCAPE: Byte = 0x29
        const val KEY_BACKSPACE: Byte = 0x2A
        const val KEY_TAB: Byte = 0x2B
        const val KEY_SPACE: Byte = 0x2C
        const val KEY_RIGHT: Byte = 0x4F
        const val KEY_LEFT: Byte = 0x50
        const val KEY_DOWN: Byte = 0x51
        const val KEY_UP: Byte = 0x52

        // Standard USB HID Consumer Control Usage IDs (Page 0x0C) - 16-bit Little Endian
        const val CONSUMER_POWER: Int = 0x0030
        const val CONSUMER_PLAY_PAUSE: Int = 0x00CD
        const val CONSUMER_STOP: Int = 0x00B7
        const val CONSUMER_NEXT_TRACK: Int = 0x00B5
        const val CONSUMER_PREV_TRACK: Int = 0x00B6
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

        // Combined HID Descriptor: Keyboard (ID 1) + Consumer Control (ID 2) + Mouse/Touchpad (ID 3)
        val HID_DESCRIPTOR = byteArrayOf(
            // --- Report ID 1: Standard Keyboard ---
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), ID_KEYBOARD,   //   Report ID (1)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (224 - Left Control)
            0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (231 - Right GUI)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x95.toByte(), 0x08.toByte(), //   Report Count (8)
            0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute) - Modifier byte
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x81.toByte(), 0x01.toByte(), //   Input (Constant) - Reserved byte
            0x95.toByte(), 0x06.toByte(), //   Report Count (6)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(), //   Input (Data, Array) - Key arrays (6 bytes)
            0xC0.toByte(),                // End Collection

            // --- Report ID 2: Consumer Control (Media, TV Navigation, Volume, Power) ---
            0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
            0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), ID_CONSUMER,   //   Report ID (2)
            0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
            0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (0x03FF)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (0x03FF)
            0x75.toByte(), 0x10.toByte(), //   Report Size (16)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x81.toByte(), 0x00.toByte(), //   Input (Data, Array)
            0xC0.toByte(),                // End Collection

            // --- Report ID 3: Relative Mouse (Touchpad) ---
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), ID_MOUSE,      //   Report ID (3)
            0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
            0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), //     Usage Minimum (Button 1)
            0x29.toByte(), 0x03.toByte(), //     Usage Maximum (Button 3)
            0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x75.toByte(), 0x01.toByte(), //     Report Size (1)
            0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), //     Report Count (1)
            0x75.toByte(), 0x05.toByte(), //     Report Size (5)
            0x81.toByte(), 0x01.toByte(), //     Input (Constant) - Padding
            0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //     Usage (X)
            0x09.toByte(), 0x31.toByte(), //     Usage (Y)
            0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), //     Report Size (8)
            0x95.toByte(), 0x02.toByte(), //     Report Count (2)
            0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
            0xC0.toByte(),                //   End Collection
            0xC0.toByte()                 // End Collection
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var hidDevice: BluetoothHidDevice? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _statusText = MutableStateFlow("Initializing Bluetooth HID...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.name}")
            _isRegistered.value = registered
            if (registered) {
                _statusText.value = if (_connectedDevice.value != null) "Connected" else "Ready to Connect"
                refreshPairedDevices()
                if (pluggedDevice != null) {
                    _connectedDevice.value = pluggedDevice
                    _statusText.value = "Connected"
                }
            } else {
                _statusText.value = "HID Unregistered"
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: ${device?.name} -> $state")
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
                _isRegistered.value = false
                _connectedDevice.value = null
                _statusText.value = "HID Service Disconnected"
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
            val bonded = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            _pairedDevices.value = bonded
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
        _statusText.value = "Connecting to ${device.name ?: "TV"}..."
        hid.connect(device)
    }

    fun disconnect() {
        val dev = _connectedDevice.value ?: return
        hidDevice?.disconnect(dev)
    }

    // Send Keyboard Key Press & Release
    fun sendKey(keyCode: Byte, modifier: Byte = 0x00) {
        val dev = _connectedDevice.value ?: return
        val hid = hidDevice ?: return
        executor.execute {
            try {
                val down = byteArrayOf(modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00)
                val up = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), down)
                Thread.sleep(18)
                hid.sendReport(dev, ID_KEYBOARD.toInt(), up)
                Thread.sleep(12)
            } catch (e: Exception) {
                Log.e(TAG, "sendKey failed", e)
            }
        }
    }

    // Send Consumer Control Key (Volume, Media, Home, Back, Power)
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

    // Send Touchpad Mouse Move / Tap
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

    // Type an entire string directly from mobile keyboard to TV!
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
                    Thread.sleep(16)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), up)
                    Thread.sleep(16)
                }
                if (pressEnterAfter) {
                    val down = byteArrayOf(0x00, 0x00, KEY_ENTER, 0x00, 0x00, 0x00, 0x00, 0x00)
                    val up = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), down)
                    Thread.sleep(20)
                    hid.sendReport(dev, ID_KEYBOARD.toInt(), up)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendText failed", e)
            }
        }
    }

    // Map ASCII characters to (ShiftModifier, HID KeyCode)
    private fun charToHid(c: Char): Pair<Byte, Byte>? {
        val shift: Byte = 0x02
        val none: Byte = 0x00
        return when (c) {
            in 'a'..'z' -> Pair(none, (0x04 + (c - 'a')).toByte())
            in 'A'..'Z' -> Pair(shift, (0x04 + (c - 'A')).toByte())
            in '1'..'9' -> Pair(none, (0x1E + (c - '1')).toByte())
            '0' -> Pair(none, 0x27.toByte())
            ' ' -> Pair(none, KEY_SPACE)
            '\n' -> Pair(none, KEY_ENTER)
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
            '\\' -> Pair(none, 0x31.toByte())
            '|' -> Pair(shift, 0x31.toByte())
            ';' -> Pair(none, 0x33.toByte())
            ':' -> Pair(shift, 0x33.toByte())
            '\'' -> Pair(none, 0x34.toByte())
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
