package com.dk.mitvremotebt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// Theme Colors matching the Dark Purple Glow UI
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

    // Request Bluetooth Permissions on Android 12+
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            btRemote.start()
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            btRemote.start()
        } else {
            launcher.launch(permissions)
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
            // 1. TOP HEADER BAR
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

            // 2. MODE SWITCHER TABS (Remote / Touchpad / Keyboard)
            ModeSelectorTabs(
                currentMode = topMode,
                onModeSelected = {
                    haptic()
                    topMode = it
                    bottomNav = BottomNav.REMOTE
                }
            )

            // 3. MAIN DYNAMIC CONTENT AREA
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
                                it()
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
                            onHaptic = { haptic() },
                            onOpenKeyboard = {
                                haptic()
                                topMode = TopMode.KEYBOARD
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

            // 4. BOTTOM NAVIGATION BAR
            BottomNavigationBar(
                selected = bottomNav,
                onSelect = {
                    haptic()
                    bottomNav = it
                }
            )
        }

        // Device Connection Bottom Sheet
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
            // TV Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF132A20))
                    .border(1.dp, GreenConnected.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tv,
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
            // Pair / Cast Icon Button
            IconButton(
                onClick = onSelectDevice,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CastConnected,
                    contentDescription = "Connect TV",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Power Button
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
            Triple(TopMode.REMOTE, "Remote", Icons.Rounded.SettingsRemote),
            Triple(TopMode.TOUCHPAD, "Touchpad", Icons.Outlined.TouchApp),
            Triple(TopMode.KEYBOARD, "Keyboard", Icons.Outlined.Keyboard)
        )

        items.forEach { (mode, label, icon) ->
            val selected = currentMode == mode
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) PurpleDim.copy(alpha = 0.45f) else Color.Transparent
                    )
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
    onHaptic: () -> Unit,
    onOpenKeyboard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Action Pill Row (Home, Back, Keyboard, Settings/Menu)
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
                icon = Icons.Outlined.Keyboard,
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

        // Circular D-Pad with Purple Glow Ring
        Box(
            modifier = Modifier
                .size(236.dp)
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
            // UP
            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_UP)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Up",
                    tint = TextPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            // DOWN
            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_DOWN)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Down",
                    tint = TextPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            // LEFT
            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_LEFT)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = "Left",
                    tint = TextPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            // RIGHT
            IconButton(
                onClick = {
                    onHaptic()
                    btRemote.sendKey(BtRemote.KEY_RIGHT)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = "Right",
                    tint = TextPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            // CENTER OK BUTTON
            Box(
                modifier = Modifier
                    .size(86.dp)
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
                Text(
                    text = "OK",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Volume + Mute/Voice + Channel Rocker Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // VOL Rocker
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

            // Center Mute & Voice Search
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CenterPillButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeMute,
                    tint = TextPrimary,
                    onClick = {
                        onHaptic()
                        btRemote.sendConsumer(BtRemote.CONSUMER_MUTE)
                    }
                )
                CenterPillButton(
                    icon = Icons.Rounded.Mic,
                    tint = PurpleBright,
                    onClick = {
                        onHaptic()
                        btRemote.sendConsumer(BtRemote.CONSUMER_VOICE_SEARCH)
                    }
                )
            }

            // CH Rocker
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
        // Large Touchpad Surface with Purple Corner Brackets
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
            // Corner Purple Glow Accents
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val stroke = 3.dp.toPx()
                val len = 26.dp.toPx()
                val r = 12.dp.toPx()

                // Top-Left
                drawLine(PurpleBright, Offset(0f, r), Offset(0f, len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(r, 0f), Offset(len, 0f), stroke, StrokeCap.Round)

                // Top-Right
                drawLine(PurpleBright, Offset(size.width, r), Offset(size.width, len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(size.width - r, 0f), Offset(size.width - len, 0f), stroke, StrokeCap.Round)

                // Bottom-Left
                drawLine(PurpleBright, Offset(0f, size.height - r), Offset(0f, size.height - len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(r, size.height), Offset(len, size.height), stroke, StrokeCap.Round)

                // Bottom-Right
                drawLine(PurpleBright, Offset(size.width, size.height - r), Offset(size.width, size.height - len), stroke, StrokeCap.Round)
                drawLine(PurpleBright, Offset(size.width - r, size.height), Offset(size.width - len, size.height), stroke, StrokeCap.Round)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = "Touchpad",
                    tint = TextSecondary,
                    modifier = Modifier.size(46.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Swipe to Navigate",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Tap to Select",
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Media Playback Row 1 (Rewind, Play/Pause, Fast Forward)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaButton(
                icon = Icons.Rounded.FastRewind,
                modifier = Modifier.weight(1f),
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_REWIND)
                }
            )
            MediaButton(
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier.weight(1.2f),
                highlight = true,
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_PLAY_PAUSE)
                }
            )
            MediaButton(
                icon = Icons.Rounded.FastForward,
                modifier = Modifier.weight(1f),
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_FAST_FORWARD)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Media Playback Row 2 (Back, Stop, Home)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                modifier = Modifier.weight(1f),
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_BACK)
                }
            )
            MediaButton(
                icon = Icons.Rounded.Stop,
                modifier = Modifier.weight(1.2f),
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_STOP)
                }
            )
            MediaButton(
                icon = Icons.Outlined.Home,
                modifier = Modifier.weight(1f),
                onClick = {
                    onHaptic()
                    btRemote.sendConsumer(BtRemote.CONSUMER_HOME)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                onHaptic()
                sliderPos = (sliderPos - 0.1f).coerceIn(0f, 1f)
                btRemote.sendConsumer(BtRemote.CONSUMER_VOL_DOWN)
            }) {
                Icon(Icons.AutoMirrored.Rounded.VolumeMute, contentDescription = "Vol Down", tint = TextPrimary)
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

            IconButton(onClick = {
                onHaptic()
                sliderPos = (sliderPos + 0.1f).coerceIn(0f, 1f)
                btRemote.sendConsumer(BtRemote.CONSUMER_VOL_UP)
            }) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Vol Up", tint = TextPrimary)
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
    var liveTypingMode by remember { mutableStateOf(false) }
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
                Text(
                    text = "Direct Mobile Keyboard",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onBackToRemote) {
                    Text("Done", color = PurpleBright, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "TV ke search box par click karein, phir yahan phone keyboard se seedha type karein:",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Text Input Box
            OutlinedTextField(
                value = inputText,
                onValueChange = { newText ->
                    if (liveTypingMode) {
                        if (newText.length > inputText.length) {
                            val added = newText.substring(inputText.length)
                            btRemote.sendText(added, pressEnterAfter = false)
                        } else if (newText.length < inputText.length) {
                            val diff = inputText.length - newText.length
                            repeat(diff) {
                                btRemote.sendKey(BtRemote.KEY_BACKSPACE)
                            }
                        }
                    }
                    inputText = newText
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text("Type movie, YouTube search, password...", color = TextSecondary)
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onHaptic()
                        if (!liveTypingMode && inputText.isNotEmpty()) {
                            btRemote.sendText(inputText, pressEnterAfter = true)
                            inputText = ""
                        } else {
                            btRemote.sendKey(BtRemote.KEY_ENTER)
                        }
                    }
                ),
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = { inputText = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Typing Toggle Switch
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
                        text = "Instant Live Typing",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (liveTypingMode) "Har letter turant TV par type ho raha hai" else "Type karke 'Send to TV' dabaye",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = liveTypingMode,
                    onCheckedChange = {
                        onHaptic()
                        liveTypingMode = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PurpleGlow
                    )
                )
            }
        }

        // Action Buttons (Send Text, Send + Search, Backspace, Enter)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onHaptic()
                        if (inputText.isNotEmpty()) {
                            btRemote.sendText(inputText, pressEnterAfter = false)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Text", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        onHaptic()
                        btRemote.sendText(inputText, pressEnterAfter = true)
                        inputText = ""
                    },
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleGlow)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send & Enter", fontWeight = FontWeight.Bold)
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
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TV Backspace", color = TextPrimary, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        onHaptic()
                        btRemote.sendKey(BtRemote.KEY_ENTER)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Icon(Icons.Rounded.KeyboardReturn, contentDescription = "Enter", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TV Enter / OK", color = TextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AppsShortcutsSection(onCommand: (() -> Unit) -> Unit) {
    val apps = listOf(
        Triple("YouTube", Color(0xFFDC2626), "YT"),
        Triple("Netflix", Color(0xFF171717), "N"),
        Triple("Prime Video", Color(0xFF0284C7), "Prime"),
        Triple("Hotstar", Color(0xFF1E3A8A), "Hotstar"),
        Triple("JioCinema", Color(0xFFDB2777), "Jio"),
        Triple("SonyLIV", Color(0xFF4F46E5), "Sony")
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TV Quick Shortcuts",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Mi TV Home screen par aane ke liye kisi bhi shortcut par tap karein:",
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
                                .height(82.dp)
                                .clickable {
                                    onCommand { }
                                },
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
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
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
         text = "First-time setup:\n1. Pair your Mi TV 4A in your phone's Bluetooth settings.\n2. Tap 'Mi TV 4A' at the top and select Connect.",
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
            Icon(Icons.Rounded.Bluetooth, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
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
            Triple(BottomNav.REMOTE, "Remote", Icons.Rounded.SettingsRemote),
            Triple(BottomNav.APPS, "Apps", Icons.Rounded.GridView),
            Triple(BottomNav.MORE, "More", Icons.Rounded.MoreHoriz)
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
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onMinus, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "$label Down", tint = TextPrimary)
        }
    }
}

@Composable
fun CenterPillButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun MediaButton(
    icon: ImageVector,
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
        Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
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
                        "Koi paired Bluetooth device nahi mila. Pehle phone ke Bluetooth se Mi TV ko pair karein.",
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
                                    Text(
                                        text = dev.name ?: "Unknown TV",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = dev.address,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
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
