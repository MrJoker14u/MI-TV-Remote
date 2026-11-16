package com.dk.mitvremotebt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

val BgDeep = Color(0xFF0A0B10)
val SurfaceDark = Color(0xFF151621)
val SurfaceElevated = Color(0xFF1E202E)
val PurpleGlow = Color(0xFF8B5CF6)
val PurpleBright = Color(0xFFA855F7)
val PurpleDim = Color(0xFF3B2868)
val GreenConnected = Color(0xFF22C55E)
val RedPower = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    private lateinit var btRemote: BtRemote

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btRemote = BtRemote(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDeep,
                    surface = SurfaceDark,
                    primary = PurpleGlow,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDeep
                ) {
                    MiTvRemoteScreen(btRemote)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        btRemote.stop()
    }
}

enum class TopMode { REMOTE, TOUCHPAD, KEYBOARD }
enum class BottomNav { REMOTE, APPS, MORE }

@Composable
fun MiTvRemoteScreen(btRemote: BtRemote) {
    val context = LocalContext.current
    val view = LocalView.current

    val connectedDevice by btRemote.connectedDevice.collectAsState()
    val pairedDevices by btRemote.pairedDevices.collectAsState()
    val statusText by btRemote.statusText.collectAsState()

    var topMode by remember { mutableStateOf(TopMode.REMOTE) }
    var bottomNav by remember { mutableStateOf(BottomNav.REMOTE) }
    var showDeviceSheet by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startPhoneVoiceToTv() {
        if (speechRecognizer == null) {
            btRemote.sendConsumer(BtRemote.CONSUMER_VOICE_SEARCH)
            return
        }

        btRemote.sendConsumer(BtRemote.CONSUMER_VOICE_SEARCH)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceStatus = "Listening on phone mic..."
            }
            override fun onBeginningOfSpeech() {
                voiceStatus = "Speak now..."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                voiceStatus = "Sending to TV..."
            }
            override fun onError(error: Int) {
                isListening = false
                voiceStatus = ""
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    voiceStatus = "Sent: $spoken"
                    btRemote.sendText(spoken, pressEnterAfter = true)
                } else {
                    voiceStatus = ""
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    voiceStatus = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        btRemote.start()
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startPhoneVoiceToTv()
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            btRemote.start()
        } else {
            permLauncher.launch(permissions)
        }
    }

    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF28184C).copy(alpha = 0.45f),
                        BgDeep
                    ),
                    center = Offset(700f, 950f),
                    radius = 900f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopHeaderBar(
                deviceName = connectedDevice?.name ?: "Mi TV 4A",
                isConnected = connectedDevice != null,
                statusText = statusText,
                onSelectDevice = {
                    haptic()
                    btRemote.refreshPairedDevices()
                    showDeviceSheet = true
                },
                onPowerClick = {
                    haptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_POWER)
                }
            )

            ModeSelectorTabs(
                currentMode = topMode,
                onModeSelected = {
                    haptic()
                    topMode = it
                    bottomNav = BottomNav.REMOTE
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bottomNav == BottomNav.APPS -> {
                        AppsShortcutsSection(
                            onCommand = {
                                haptic()
                                btRemote.sendConsumer(BtRemote.CONSUMER_HOME)
                            }
                        )
                    }
                    bottomNav == BottomNav.MORE -> {
                        MoreControlsSection(
                            btRemote = btRemote,
                            onHaptic = { haptic() },
                            onOpenBtSettings = {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        )
                    }
                    topMode == TopMode.REMOTE -> {
                        RemoteControlSection(
                            btRemote = btRemote,
                            isListening = isListening,
                            voiceStatus = voiceStatus,
                            onHaptic = { haptic() },
                            onOpenKeyboard = {
                                haptic()
                                topMode = TopMode.KEYBOARD
                            },
                            onVoiceClick = {
                                haptic()
                                if (isListening) {
                                    speechRecognizer?.stopListening()
                                    isListening = false
                                } else {
                                    val hasMic = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        startPhoneVoiceToTv()
                                    } else {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        )
                    }
                    topMode == TopMode.TOUCHPAD -> {
                        TouchpadControlSection(
                            btRemote = btRemote,
                            onHaptic = { haptic() }
                        )
                    }
                    topMode == TopMode.KEYBOARD -> {
                        DirectKeyboardSection(
                            btRemote = btRemote,
                            onHaptic = { haptic() },
                            onBackToRemote = {
                                haptic()
                                topMode = TopMode.REMOTE
                            }
                        )
                    }
                }
            }

            BottomNavigationBar(
                selected = bottomNav,
                onSelect = {
                    haptic()
                    bottomNav = it
                }
            )
        }

        if (showDeviceSheet) {
            DevicePickerDialog(
                pairedDevices = pairedDevices,
                connectedDevice = connectedDevice,
                onDismiss = { showDeviceSheet = false },
                onConnect = { device ->
                    btRemote.connect(device)
                    showDeviceSheet = false
                },
                onDisconnect = {
                    btRemote.disconnect()
                    showDeviceSheet = false
                },
                onOpenSystemBluetooth = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            )
        }
    }
}

@Composable
fun TopHeaderBar(
    deviceName: String,
    isConnected: Boolean,
    statusText: String,
    onSelectDevice: () -> Unit,
    onPowerClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { onSelectDevice() }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF132A20))
                    .border(1.dp, GreenConnected.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "TV",
                    tint = GreenConnected,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deviceName,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select TV",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) GreenConnected else Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "Connected" else statusText,
                        color = if (isConnected) GreenConnected else TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onSelectDevice,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Connect TV",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Dedicated Power On/Off Button
            IconButton(
                onClick = onPowerClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2B1418))
                    .border(1.dp, RedPower.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = RedPower,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ModeSelectorTabs(
    currentMode: TopMode,
    onModeSelected: (TopMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val items = listOf(
            Triple(TopMode.REMOTE, "Remote", Icons.Rounded.Home),
            Triple(TopMode.TOUCHPAD, "Touchpad", Icons.Rounded.Star),
            Triple(TopMode.KEYBOARD, "Keyboard", Icons.Rounded.Edit)
        )

        items.forEach { (mode, label, icon) ->
            val selected = currentMode == mode
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) PurpleDim.copy(alpha = 0.45f) else Color.Transparent)
                    .border(
                        width = if (selected) 1.2.dp else 0.dp,
                        color = if (selected) PurpleGlow else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) TextPrimary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    color = if (selected) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun RemoteControlSection(
    btRemote: BtRemote,
    isListening: Boolean,
    voiceStatus: String,
    onHaptic: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PillActionButton(
                icon = Icons.Outlined.Home,
                desc = "Home",
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_HOME)
                }
            )
            PillActionButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                desc = "Back",
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_BACK)
                }
            )
            PillActionButton(
                icon = Icons.Rounded.Edit,
                desc = "Keyboard",
                highlight = true,
                onClick = onOpenKeyboard
            )
            PillActionButton(
                icon = Icons.Outlined.Settings,
                desc = "Menu",
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_MENU)
                }
            )
        }

        Box(
            modifier = Modifier
                .size(230.dp)
                .shadow(24.dp, CircleShape, ambientColor = PurpleGlow, spotColor = PurpleGlow)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E2030), Color(0xFF12131C))
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(PurpleBright, PurpleDim, Color(0xFF232536))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_UP)
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(56.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Up", tint = TextPrimary, modifier = Modifier.size(34.dp))
            }

            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_DOWN)
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(56.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Down", tint = TextPrimary, modifier = Modifier.size(34.dp))
            }

            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_LEFT)
                },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(56.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Left", tint = TextPrimary, modifier = Modifier.size(34.dp))
            }

            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_RIGHT)
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(56.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Right", tint = TextPrimary, modifier = Modifier.size(34.dp))
            }

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF282A3E), Color(0xFF161824))
                        )
                    )
                    .border(1.5.dp, PurpleGlow.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        onHaptic()
                        btRemote.sendKey(BtRemote.KEY_ENTER)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "OK", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (voiceStatus.isNotEmpty()) {
            Text(
                text = voiceStatus,
                color = PurpleBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalRocker(
                label = "VOL",
                onPlus = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_VOL_UP)
                },
                onMinus = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_VOL_DOWN)
                }
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .clickable {
                            onHaptic()
                            btRemote.sendConsumer(BtRemote.CONSUMER_MUTE)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("MUTE", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isListening) RedPower.copy(alpha = 0.25f) else SurfaceDark)
                        .border(
                            1.5.dp,
                            if (isListening) RedPower else PurpleGlow.copy(alpha = 0.5f),
                            RoundedCornerShape(24.dp)
                        )
                        .clickable { onVoiceClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Voice Search",
                        tint = if (isListening) RedPower else PurpleBright,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            VerticalRocker(
                label = "CH",
                onPlus = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_CH_UP)
                },
                onMinus = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_CH_DOWN)
                }
            )
        }
    }
}

@Composable
fun TouchpadControlSection(
    btRemote: BtRemote,
    onHaptic: () -> Unit
) {
    var sliderPos by remember { mutableFloatStateOf(0.5f) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onHaptic()
                            btRemote.sendMouse(0, 0, leftClick = true)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = (dragAmount.x * 1.4f).toInt()
                        val dy = (dragAmount.y * 1.4f).toInt()
                        if (dx != 0 || dy != 0) {
                            btRemote.sendMouse(dx, dy, leftClick = false)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val stroke = 3.dp.toPx()
                val len = 26.dp.toPx()
                val r = 12.dp.toPx()

                drawLine(PurpleBright, Offset(0f, r), Offset(0f, len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(r, 0f), Offset(len, 0f), stroke, StrokeCap.Round)

                drawLine(PurpleBright, Offset(size.width, r), Offset(size.width, len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(size.width - r, 0f), Offset(size.width - len, 0f), stroke, StrokeCap.Round)

                drawLine(PurpleBright, Offset(0f, size.height - r), Offset(0f, size.height - len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(r, size.height), Offset(len, size.height), stroke, StrokeCap.Round)

                drawLine(PurpleBright, Offset(size.width, size.height - r), Offset(size.width, size.height - len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(size.width - r, size.height), Offset(size.width - len, size.height), stroke, StrokeCap.Round)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "TOUCHPAD", color = PurpleBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Swipe to Navigate", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(text = "Tap to Select", color = TextSecondary.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaTextButton(label = "REW", modifier = Modifier.weight(1f)) {
                onHaptic()
                btRemote.sendConsumer(BtRemote.CONSUMER_REWIND)
            }
            MediaTextButton(label = "PLAY / PAUSE", modifier = Modifier.weight(1.4f), highlight = true) {
                onHaptic()
                btRemote.sendConsumer(BtRemote.CONSUMER_PLAY_PAUSE)
            }
            MediaTextButton(label = "FWD", modifier = Modifier.weight(1f)) {
                onHaptic()
                btRemote.sendConsumer(BtRemote.CONSUMER_FAST_FORWARD)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaTextButton(label = "BACK", modifier = Modifier.weight(1f)) {
                onHaptic()
                btRemote.sendConsumer(BtRemote.CONSUMER_BACK)
            }
            MediaTextButton(label = "STOP", modifier = Modifier.weight(1.4f)) {
                onHaptic()
                btRemote.sendConsumer(BtRemote.CONSUMER_STOP)
            }
            MediaTextButton(label = "HOME", modifier = Modifier.weight(1f)) {
                onHaptic()
                btRemote.sendConsumer(BtRemote.CONSUMER_HOME)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                onHaptic()
                sliderPos = (sliderPos - 0.1f).coerceIn(0f, 1f)
                btRemote.sendConsumer(BtRemote.CONSUMER_VOL_DOWN)
            }) {
                Text("VOL -", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = sliderPos,
                onValueChange = { newVal ->
                    if (newVal > sliderPos + 0.06f) {
                        btRemote.sendConsumer(BtRemote.CONSUMER_VOL_UP)
                        sliderPos = newVal
                    } else if (newVal < sliderPos - 0.06f) {
                        btRemote.sendConsumer(BtRemote.CONSUMER_VOL_DOWN)
                        sliderPos = newVal
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = PurpleBright,
                    inactiveTrackColor = SurfaceElevated
                )
            )

            TextButton(onClick = {
                onHaptic()
                sliderPos = (sliderPos + 0.1f).coerceIn(0f, 1f)
                btRemote.sendConsumer(BtRemote.CONSUMER_VOL_UP)
            }) {
                Text("VOL +", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DirectKeyboardSection(
    btRemote: BtRemote,
    onHaptic: () -> Unit,
    onBackToRemote: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var liveSyncEnabled by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, PurpleGlow.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Live TV Keyboard",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (liveSyncEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GreenConnected.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("LIVE SYNC", color = GreenConnected, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TextButton(onClick = onBackToRemote) {
                    Text("Done", color = PurpleBright, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "Open any search box on TV (Play Store, YouTube, Netflix) and type here—every letter syncs instantly to your TV.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { newText ->
                    if (liveSyncEnabled) {
                        var commonLen = 0
                        val minLen = minOf(inputText.length, newText.length)
                        while (commonLen < minLen && inputText[commonLen] == newText[commonLen]) {
                            commonLen++
                        }
                        val backspacesNeeded = inputText.length - commonLen
                        val addedText = newText.substring(commonLen)

                        repeat(backspacesNeeded) {
                            btRemote.sendKey(BtRemote.KEY_BACKSPACE)
                        }
                        if (addedText.isNotEmpty()) {
                            btRemote.sendText(addedText, pressEnterAfter = false)
                        }
                    }
                    inputText = newText
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text("Type here to search on TV...", color = TextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleBright,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PurpleBright
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onHaptic()
                        if (!liveSyncEnabled && inputText.isNotEmpty()) {
                            btRemote.sendText(inputText, pressEnterAfter = true)
                        } else {
                            btRemote.sendKey(BtRemote.KEY_ENTER)
                        }
                    }
                ),
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = {
                            onHaptic()
                            if (liveSyncEnabled) {
                                btRemote.clearTvInputField()
                            }
                            inputText = ""
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear All", tint = TextSecondary)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instant Live Sync",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (liveSyncEnabled) "Typing syncs directly to TV in real time" else "Type full text first, then tap Send",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = liveSyncEnabled,
                    onCheckedChange = {
                        onHaptic()
                        liveSyncEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PurpleGlow
                    )
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onHaptic()
                        btRemote.clearTvInputField()
                        inputText = ""
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear TV Box", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        onHaptic()
                        if (!liveSyncEnabled && inputText.isNotEmpty()) {
                            btRemote.sendText(inputText, pressEnterAfter = true)
                        } else {
                            btRemote.sendKey(BtRemote.KEY_ENTER)
                        }
                    },
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleGlow)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (liveSyncEnabled) "Search / Enter" else "Send & Search", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onHaptic()
                        btRemote.sendKey(BtRemote.KEY_BACKSPACE)
                        if (inputText.isNotEmpty()) {
                            inputText = inputText.dropLast(1)
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Text("Backspace", color = TextPrimary, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        onHaptic()
                        btRemote.sendKey(BtRemote.KEY_SPACE)
                        if (liveSyncEnabled) {
                            inputText += " "
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Text("Space", color = TextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AppsShortcutsSection(onCommand: () -> Unit) {
    val apps = listOf(
        Triple("YouTube", Color(0xFFDC2626), "YT"),
        Triple("Netflix", Color(0xFF171717), "N"),
        Triple("Prime Video", Color(0xFF0284C7), "Prime"),
        Triple("Hotstar", Color(0xFF1E3A8A), "Hotstar"),
        Triple("Play Store", Color(0xFF059669), "Play"),
        Triple("SonyLIV", Color(0xFF4F46E5), "Sony")
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TV Quick Launcher",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Tap any app card to jump straight to your TV Home screen:",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.chunked(2).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowApps.forEach { (name, bg, badge) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clickable { onCommand() },
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = badge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreControlsSection(
    btRemote: BtRemote,
    onHaptic: () -> Unit,
    onOpenBtSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Mi TV Setup & Extra Keys",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "First-time setup:
1. Pair your Mi TV 4A in your phone's Bluetooth settings.
2. Tap 'Mi TV 4A' at the top and select Connect.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Button(
            onClick = {
                onHaptic()
                onOpenBtSettings()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurpleGlow)
        ) {
            Text("Open Phone Bluetooth Settings", fontWeight = FontWeight.SemiBold)
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_ESCAPE)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ESC / Exit", color = TextPrimary)
            }
            OutlinedButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_TAB)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("TAB Next", color = TextPrimary)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selected: BottomNav,
    onSelect: (BottomNav) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceDark)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val navItems = listOf(
            Triple(BottomNav.REMOTE, "Remote", Icons.Rounded.Home),
            Triple(BottomNav.APPS, "Apps", Icons.Rounded.Menu),
            Triple(BottomNav.MORE, "More", Icons.Rounded.MoreVert)
        )

        navItems.forEach { (item, label, icon) ->
            val isSelected = selected == item
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) PurpleDim.copy(alpha = 0.45f) else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) PurpleGlow else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) TextPrimary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun PillActionButton(
    icon: ImageVector,
    desc: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (highlight) PurpleDim.copy(alpha = 0.45f) else SurfaceDark)
            .border(
                1.dp,
                if (highlight) PurpleGlow else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(24.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (highlight) PurpleBright else TextPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun VerticalRocker(
    label: String,
    onPlus: () -> Unit,
    onMinus: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .height(146.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(SurfaceDark)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(33.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPlus, modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "$label Up", tint = TextPrimary)
        }
        Text(text = label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onMinus, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "$label Down", tint = TextPrimary)
        }
    }
}

@Composable
fun MediaTextButton(
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(if (highlight) SurfaceElevated else SurfaceDark)
            .border(
                1.dp,
                if (highlight) PurpleGlow.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(25.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DevicePickerDialog(
    pairedDevices: List<BluetoothDevice>,
    connectedDevice: BluetoothDevice?,
    onDismiss: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onOpenSystemBluetooth: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Select Mi TV Device", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (pairedDevices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair your Mi TV in phone Bluetooth settings first.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(pairedDevices) { dev ->
                            val isCurrent = connectedDevice?.address == dev.address
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isCurrent) onDisconnect() else onConnect(dev)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = dev.name ?: "Unknown TV", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(text = dev.address, color = TextSecondary, fontSize = 11.sp)
                                }
                                Text(
                                    text = if (isCurrent) "Disconnect" else "Connect",
                                    color = if (isCurrent) RedPower else PurpleBright,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSystemBluetooth) {
                Text("Pair New TV", color = PurpleBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
