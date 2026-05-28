package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.VolumePreset
import com.example.service.VolumeSettings
import com.example.viewmodel.VolumeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeScreen(
    viewModel: VolumeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // Read reactive states
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val presets by viewModel.allPresets.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Floating handle options
    var isHandleEnabled by remember { mutableStateOf(viewModel.settings.isHandleEnabled) }
    var handleSide by remember { mutableStateOf(viewModel.settings.handleSide) }
    var handleOpacity by remember { mutableStateOf(viewModel.settings.handleOpacity) }
    var handleHeight by remember { mutableStateOf(viewModel.settings.handleHeight.toFloat()) }
    var handleYOffset by remember { mutableStateOf(viewModel.settings.handleYOffset.toFloat()) }
    var handleDragAction by remember { mutableStateOf(viewModel.settings.handleDragAction) }
    var isCompactMode by remember { mutableStateOf(viewModel.settings.isCompactMode) }

    // On-screen direct testing sliders
    var liveMedia by remember { mutableStateOf(viewModel.getStreamPercentage(AudioManager.STREAM_MUSIC)) }
    var liveRing by remember { mutableStateOf(viewModel.getStreamPercentage(AudioManager.STREAM_RING)) }
    var liveAlarm by remember { mutableStateOf(viewModel.getStreamPercentage(AudioManager.STREAM_ALARM)) }
    var liveNotif by remember { mutableStateOf(viewModel.getStreamPercentage(AudioManager.STREAM_NOTIFICATION)) }

    // Activity result launcher for Overlay settings request
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        // Auto start service if permitted and turned on
        if (hasOverlayPermission && viewModel.settings.isServiceRunning && !isServiceRunning) {
            viewModel.startVolumeService()
        }
    }

    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Refresh live sliders on start or resume
    LaunchedEffect(Unit) {
        liveMedia = viewModel.getStreamPercentage(AudioManager.STREAM_MUSIC)
        liveRing = viewModel.getStreamPercentage(AudioManager.STREAM_RING)
        liveAlarm = viewModel.getStreamPercentage(AudioManager.STREAM_ALARM)
        liveNotif = viewModel.getStreamPercentage(AudioManager.STREAM_NOTIFICATION)
    }

    // Keep app UI sliders in Sync with hardware physical buttons in real time
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    liveMedia = viewModel.getStreamPercentage(AudioManager.STREAM_MUSIC)
                    liveRing = viewModel.getStreamPercentage(AudioManager.STREAM_RING)
                    liveAlarm = viewModel.getStreamPercentage(AudioManager.STREAM_ALARM)
                    liveNotif = viewModel.getStreamPercentage(AudioManager.STREAM_NOTIFICATION)
                }
            }
        }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: java.lang.IllegalArgumentException) {
                // Avoid unregister mismatch if receiver wasn't registered
            }
        }
    }

    // Scrollable primary view
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Điều Khiển Âm Lượng",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // Service Controller Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceRunning) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Dịch vụ âm lượng nổi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isServiceRunning) Color(0xFF2ECC71) else Color(0xFFE74C3C))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isServiceRunning) "Đang hoạt động" else "Đang tạm dừng",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isServiceRunning) Color(0xFF27AE60) else Color(0xFFC0392B)
                                    )
                                }
                            }

                            Switch(
                                checked = isServiceRunning,
                                onCheckedChange = { active ->
                                    if (active) {
                                        // Request notification permission if needed
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }

                                        if (!hasOverlayPermission) {
                                            // Launch overlay setting direct routing
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            overlayPermissionLauncher.launch(intent)
                                        } else {
                                            viewModel.startVolumeService()
                                        }
                                    } else {
                                        viewModel.stopVolumeService()
                                    }
                                },
                                modifier = Modifier.testTag("service_toggle_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Hỗ trợ thay đổi âm lượng không cần sử dụng phím cứng. Thao tác thông qua phím nổi bên ngoài màn hình hoặc thanh thông báo hệ thống.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Permissions Check panel
            if (!hasOverlayPermission || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yêu cầu cấp quyền hoạt động",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            if (!hasOverlayPermission) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Vẽ trên ứng dụng khác (Overlay)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Yêu cầu để hiển thị phím nổi tăng giảm âm lượng.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                overlayPermissionLauncher.launch(intent)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Cấp quyền", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Thông báo",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Yêu cầu để ghim phím điều khiển âm lượng trên thanh trạng thái.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Cho phép", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Sliders for Direct Testing
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Chỉnh âm lượng trực tiếp 🔊",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IosVolumeSlider(
                                percent = liveMedia.toFloat(),
                                onPercentChange = { pct ->
                                    viewModel.setStreamPercentage(AudioManager.STREAM_MUSIC, pct.toInt(), playSound = false)
                                },
                                onPercentChangeFinished = { pct ->
                                    liveMedia = pct.toInt()
                                    viewModel.setStreamPercentage(AudioManager.STREAM_MUSIC, pct.toInt(), playSound = true)
                                },
                                icon = Icons.Default.MusicNote,
                                label = "Media"
                            )

                            IosVolumeSlider(
                                percent = liveRing.toFloat(),
                                onPercentChange = { pct ->
                                    viewModel.setStreamPercentage(AudioManager.STREAM_RING, pct.toInt(), playSound = false)
                                },
                                onPercentChangeFinished = { pct ->
                                    liveRing = pct.toInt()
                                    viewModel.setStreamPercentage(AudioManager.STREAM_RING, pct.toInt(), playSound = true)
                                },
                                icon = Icons.Default.RingVolume,
                                label = "Chuông"
                            )

                            IosVolumeSlider(
                                percent = liveNotif.toFloat(),
                                onPercentChange = { pct ->
                                    viewModel.setStreamPercentage(AudioManager.STREAM_NOTIFICATION, pct.toInt(), playSound = false)
                                },
                                onPercentChangeFinished = { pct ->
                                    liveNotif = pct.toInt()
                                    viewModel.setStreamPercentage(AudioManager.STREAM_NOTIFICATION, pct.toInt(), playSound = true)
                                },
                                icon = Icons.Default.Notifications,
                                label = "T.Báo"
                            )

                            IosVolumeSlider(
                                percent = liveAlarm.toFloat(),
                                onPercentChange = { pct ->
                                    viewModel.setStreamPercentage(AudioManager.STREAM_ALARM, pct.toInt(), playSound = false)
                                },
                                onPercentChangeFinished = { pct ->
                                    liveAlarm = pct.toInt()
                                    viewModel.setStreamPercentage(AudioManager.STREAM_ALARM, pct.toInt(), playSound = true)
                                },
                                icon = Icons.Default.Alarm,
                                label = "Báo thức"
                            )
                        }
                    }
                }
            }

            // Edge Handle Options Card (only relevant if overlay is permitted)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thiết lập phím nổi ⚙️",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Switch(
                                checked = isHandleEnabled,
                                onCheckedChange = { isEnabled ->
                                    isHandleEnabled = isEnabled
                                    viewModel.settings.isHandleEnabled = isEnabled
                                }
                            )
                        }

                        AnimatedVisibility(visible = isHandleEnabled) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Side Placement Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Bên màn hình:", fontSize = 13.sp)
                                    Row {
                                        Button(
                                            onClick = {
                                                handleSide = "LEFT"
                                                viewModel.settings.handleSide = "LEFT"
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (handleSide == "LEFT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.padding(end = 4.dp).height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text("Trái", fontSize = 11.sp, color = if (handleSide == "LEFT") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Button(
                                            onClick = {
                                                handleSide = "RIGHT"
                                                viewModel.settings.handleSide = "RIGHT"
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (handleSide == "RIGHT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text("Phải", fontSize = 11.sp, color = if (handleSide == "RIGHT") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                // Giao diện siêu rút gọn Option
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Giao diện siêu rút gọn:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = "Hiện slider Media lớn ngay bên cạnh ngón tay giúp chỉnh cực nhanh", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    }
                                    Switch(
                                        checked = isCompactMode,
                                        onCheckedChange = { compact ->
                                            isCompactMode = compact
                                            viewModel.settings.isCompactMode = compact
                                        }
                                    )
                                }

                                // Height Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Chiều dài phím nổi:", fontSize = 13.sp)
                                        Text(text = "${handleHeight.toInt()} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = handleHeight,
                                        onValueChange = { h ->
                                            handleHeight = h
                                        },
                                        onValueChangeFinished = {
                                            viewModel.settings.handleHeight = handleHeight.toInt()
                                        },
                                        valueRange = 40f..150f,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Opacity Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Độ trong suốt:", fontSize = 13.sp)
                                        Text(text = "${(handleOpacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = handleOpacity,
                                        onValueChange = { o ->
                                            handleOpacity = o
                                        },
                                        onValueChangeFinished = {
                                            viewModel.settings.handleOpacity = handleOpacity
                                        },
                                        valueRange = 0.15f..1.0f,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Y-offset Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Vị trí chiều dọc (Y-offset):", fontSize = 13.sp)
                                        val labelY = if (handleYOffset == 0f) "Chính giữa" else (if (handleYOffset < 0f) "Lên trên: ${(-handleYOffset).toInt()}px" else "Xuống dưới: ${handleYOffset.toInt()}px")
                                        Text(text = labelY, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = handleYOffset,
                                        onValueChange = { y ->
                                            handleYOffset = y
                                        },
                                        onValueChangeFinished = {
                                            viewModel.settings.handleYOffset = handleYOffset.toInt()
                                        },
                                        valueRange = -600f..600f,
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Handle Swipe Gesture Option
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = "Cử chỉ vuốt phím nổi (Vuốt lên/xuống):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    handleDragAction = "VOLUME"
                                                    viewModel.settings.handleDragAction = "VOLUME"
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (handleDragAction == "VOLUME") 
                                                    MaterialTheme.colorScheme.primaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            border = if (handleDragAction == "VOLUME") 
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary) 
                                            else null
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = if (handleDragAction == "VOLUME") 
                                                        MaterialTheme.colorScheme.primary 
                                                    else 
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Tăng/Giảm âm lượng\n(Ứng cử viên số 1 🎵)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 12.sp
                                                )
                                            }
                                        }

                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    handleDragAction = "MOVE"
                                                    viewModel.settings.handleDragAction = "MOVE"
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (handleDragAction == "MOVE") 
                                                    MaterialTheme.colorScheme.primaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            border = if (handleDragAction == "MOVE") 
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary) 
                                            else null
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Default.UnfoldMore,
                                                    contentDescription = null,
                                                    tint = if (handleDragAction == "MOVE") 
                                                        MaterialTheme.colorScheme.primary 
                                                    else 
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Di chuyển phím nổi\n(Lên/Xuống ↕)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Draggable tutorial & Reset Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💡 Có thể kéo trực tiếp phím nổi để đổi vị trí!",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    TextButton(
                                        onClick = {
                                            handleYOffset = 0f
                                            viewModel.settings.handleYOffset = 0
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Đặt lại", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Profiles Config List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Danh sách cấu hình âm lượng ⚡",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Preset",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Render existing preset cards or instruction if empty
            if (presets.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nhấp vào nút '+' để lưu cấu hình âm lượng của bạn",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(presets, key = { it.id }) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Apply preset
                                viewModel.applyPreset(preset)
                                // refresh sliders on screen
                                liveMedia = preset.mediaVol
                                liveRing = preset.ringVol
                                liveAlarm = preset.alarmVol
                                liveNotif = preset.notificationVol
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Nhạc: ${preset.mediaVol}% | Chuông: ${preset.ringVol}% | TB: ${preset.notificationVol}% | Báo thức: ${preset.alarmVol}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Delete button (hide for system defaults to preserve them)
                            if (!preset.isSystemPreset) {
                                IconButton(
                                    onClick = { viewModel.deletePreset(preset) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete preset",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Preset Dialog
    if (showAddDialog) {
        var presetName by remember { mutableStateOf("") }
        var mediaVal by remember { mutableStateOf(50) }
        var ringVal by remember { mutableStateOf(50) }
        var alarmVal by remember { mutableStateOf(50) }
        var notifVal by remember { mutableStateOf(50) }

        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Lưu cấu hình mới",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = presetName,
                        onValueChange = {
                            presetName = it
                            isError = false
                        },
                        label = { Text("Tên cấu hình (Ví dụ: Xem Phim) ✍️") },
                        isError = isError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    if (isError) {
                        Text(
                            text = "Vui lòng nhập tên cấu hình hợp lệ!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    SimpleVolumeSlider(
                        title = "Nhạc & Media 🎧",
                        percent = mediaVal,
                        onPercentChange = { mediaVal = it }
                    )

                    SimpleVolumeSlider(
                        title = "Chuông điện thoại 📞",
                        percent = ringVal,
                        onPercentChange = { ringVal = it }
                    )

                    SimpleVolumeSlider(
                        title = "Thông báo hệ thống 🔔",
                        percent = notifVal,
                        onPercentChange = { notifVal = it }
                    )

                    SimpleVolumeSlider(
                        title = "Báo thức ⏰",
                        percent = alarmVal,
                        onPercentChange = { alarmVal = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetName.trim().isEmpty()) {
                            isError = true
                        } else {
                            viewModel.addNewPreset(
                                name = presetName.trim(),
                                mediaVol = mediaVal,
                                ringVol = ringVal,
                                alarmVol = alarmVal,
                                notificationVol = notifVal
                            )
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Lưu cấu hình")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun SimpleVolumeSlider(
    title: String,
    percent: Int,
    onPercentChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percent%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onPercentChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier
                .height(32.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun SliderIcon(
    percentProvider: () -> Float,
    icon: ImageVector,
    activeColor: Color,
    inactiveColor: Color
) {
    val isOverlapped = percentProvider() > 15f
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isOverlapped) activeColor else inactiveColor,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
fun LocalPercentText(
    percentProvider: () -> Float,
    color: Color
) {
    Text(
        text = "${percentProvider().toInt()}%",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center
    )
}

@Composable
fun IosVolumeSlider(
    percent: Float, // 0f..100f
    onPercentChange: (Float) -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    width: androidx.compose.ui.unit.Dp = 54.dp,
    onPercentChangeFinished: ((Float) -> Unit)? = null
) {
    var boxHeight by remember { mutableStateOf(1) }
    val isDark = isSystemInDarkTheme()
    
    // Decouple dragging state for instantaneous local rendering on weak devices
    var isDragging by remember { mutableStateOf(false) }
    var localPercent by remember { mutableStateOf(percent) }
    
    // Sync with external percentage only when NOT actively dragging
    LaunchedEffect(percent) {
        if (!isDragging) {
            localPercent = percent
        }
    }
    
    val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val fillColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(20.dp))
                .background(trackColor)
                .drawBehind {
                    val fillHeight = size.height * (localPercent / 100f).coerceIn(0f, 1f)
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(0f, size.height - fillHeight),
                        size = Size(size.width, fillHeight)
                    )
                }
                .onSizeChanged { boxHeight = if (it.height > 0) it.height else 1 }
                .pointerInput(boxHeight) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            
                            val y = down.position.y
                            val fraction = ((boxHeight - y).toFloat() / boxHeight.toFloat()).coerceIn(0f, 1f)
                            localPercent = fraction * 100f
                            onPercentChange(localPercent)
                            down.consume()
                            
                            var dragEvent = down
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    break
                                }
                                
                                val change = event.changes.firstOrNull { it.id == dragEvent.id } ?: event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val currentY = change.position.y
                                    val currentFraction = ((boxHeight - currentY).toFloat() / boxHeight.toFloat()).coerceIn(0f, 1f)
                                    localPercent = currentFraction * 100f
                                    onPercentChange(localPercent)
                                    change.consume()
                                    dragEvent = change
                                } else {
                                    break
                                }
                            }
                            
                            isDragging = false
                            onPercentChangeFinished?.invoke(localPercent)
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                SliderIcon(
                    percentProvider = { localPercent },
                    icon = icon,
                    activeColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(width + 12.dp)
        )
        
        LocalPercentText(
            percentProvider = { localPercent },
            color = textColor
        )
    }
}
