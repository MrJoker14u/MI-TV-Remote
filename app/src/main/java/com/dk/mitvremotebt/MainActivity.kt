package com.dk.mitvremotebt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.*
import android.widget.*
import kotlin.concurrent.thread
import kotlin.math.abs

@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    private lateinit var bt: BtRemote
    private lateinit var status: TextView
    private lateinit var textBox: EditText

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    private fun net(f: () -> Unit) = thread { try { f() } catch (e: Exception) { toast("Error: ${e.message}") } }

    private fun btn(label: String, w: Float = 1f, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        layoutParams = LinearLayout.LayoutParams(0, dp(52), w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
    }
    private fun key(label: String, k: Key) = btn(label) { net { bt.key(k) } }
    private fun row(vararg v: View) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; v.forEach { addView(it) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        bt = BtRemote(this) { s -> runOnUiThread { status.text = s } }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(24), dp(12), dp(12)) }
        status = TextView(this).apply { text = "Starting..."; textSize = 14f; setTextColor(Color.DKGRAY) }
        root.addView(status)

        root.addView(row(
            btn("Discoverable", 1f) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
            },
            btn("TV Chuno", 1f) { pickTv() }))

        val pad = TextView(this).apply {
            text = "Touchpad: swipe = arrows, tap = OK"; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#37474F"))
            layoutParams = LinearLayout.LayoutParams(-1, dp(130)).apply { setMargins(dp(3), dp(6), dp(3), dp(6)) }
        }
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { net { bt.key(Key.OK) }; return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val k = if (abs(vx) > abs(vy)) (if (vx > 0) Key.RIGHT else Key.LEFT) else (if (vy > 0) Key.DOWN else Key.UP)
                net { bt.key(k) }; return true
            }
        })
        pad.setOnTouchListener { _, e -> gd.onTouchEvent(e) }
        root.addView(pad)

        root.addView(row(key("Back", Key.BACK), key("▲", Key.UP), key("Home", Key.HOME)))
        root.addView(row(key("◀", Key.LEFT), key("OK", Key.OK), key("▶", Key.RIGHT)))
        root.addView(row(key("Menu", Key.MENU), key("▼", Key.DOWN), key("Power", Key.POWER)))
        root.addView(row(key("Vol −", Key.VOL_DOWN), key("Mute", Key.MUTE), key("Vol +", Key.VOL_UP)))
        root.addView(row(key("⏯", Key.PLAY_PAUSE), key("CH −", Key.CH_DOWN), key("CH +", Key.CH_UP)))

        textBox = EditText(this).apply { hint = "Yahan type karo → TV me jayega"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        root.addView(row(textBox,
            btn("Send") { val t = textBox.text.toString(); net { bt.type(t) }; textBox.setText("") },
            btn("⌫") { net { bt.key(Key.DEL) } }))
        root.addView(row(
            btn("🎤 Mic (bolo → TV me type)", 2f) { listen() },
            btn("TV Assist") { net { bt.key(Key.ASSIST) } }))

        setContentView(ScrollView(this).apply { addView(root) })

        val need = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
        if (need.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) bt.start() else requestPermissions(need, 1)
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) bt.start()
        else status.text = "Bluetooth permission do, tabhi chalega"
    }

    private fun pickTv() {
        val list = bt.bonded()
        if (list.isEmpty()) { toast("Pehle TV se pair karo (Bluetooth settings)"); return }
        AlertDialog.Builder(this).setTitle("TV chuno")
            .setItems(list.map { it.name ?: it.address }.toTypedArray()) { _, i -> bt.connect(list[i]) }.show()
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }
        try { startActivityForResult(i, 77) } catch (e: Exception) { toast("Voice input available nahi hai") }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77 && res == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            textBox.setText(t)
            net { bt.type(t); bt.key(Key.ENTER) }
        }
    }

    override fun onDestroy() { bt.stop(); super.onDestroy() }
}
