package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.database.AppDatabase
import com.example.database.VolumePreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

class VolumeControlService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingHandleView: android.view.View? = null
    private var overlayPanelView: android.view.View? = null
    private var floatingHandleLifecycleOwner: ServiceLifecycleOwner? = null
    private var overlayPanelLifecycleOwner: ServiceLifecycleOwner? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var settings: VolumeSettings
    private lateinit var audioManager: AudioManager
    private lateinit var database: AppDatabase

    companion object {
        private const val CHANNEL_ID = "volume_control_channel_v1"
        private const val NOTIFICATION_ID = 20261
        
        const val ACTION_VOL_UP = "com.example.VOLUME_UP"
        const val ACTION_VOL_DOWN = "com.example.VOLUME_DOWN"
        const val ACTION_MUTE_TOGGLE = "com.example.MUTE_TOGGLE"
        const val ACTION_SHOW_OVERLAY = "com.example.SHOW_OVERLAY"

        // Companion flows to dynamically sync with main activity UI
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _isOverlayVisible = MutableStateFlow(false)
        val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settings = VolumeSettings(this)
        database = AppDatabase.getDatabase(this, serviceScope)
        _isRunning.value = true

        createNotificationChannel()
        startServiceInForeground()

        // Reactively listen to settings updates to redraw the floating handle if needed
        serviceScope.launch {
            combineSettingsFlows().collectLatest {
                setupFloatingHandle()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun combineSettingsFlows() = kotlinx.coroutines.flow.combine(
        kotlinx.coroutines.flow.combine(
            settings.isHandleEnabledFlow,
            settings.handleSideFlow,
            settings.handleOpacityFlow,
            ::Triple
        ),
        kotlinx.coroutines.flow.combine(
            settings.handleHeightFlow,
            settings.handleYOffsetFlow,
            settings.handleDragActionFlow,
            ::Triple
        )
    ) { part1, part2 ->
        Sextuple(
            part1.first,
            part1.second,
            part1.third,
            part2.first,
            part2.second,
            part2.third
        )
    }

    private data class Sextuple<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleNotificationAction(action)
        }
        return START_STICKY
    }

    private fun handleNotificationAction(action: String) {
        when (action) {
            ACTION_VOL_UP -> {
                adjustMusicVolume(increase = true)
                updateNotification()
            }
            ACTION_VOL_DOWN -> {
                adjustMusicVolume(increase = false)
                updateNotification()
            }
            ACTION_MUTE_TOGGLE -> {
                toggleMusicMute()
                updateNotification()
            }
            ACTION_SHOW_OVERLAY -> {
                showOverlayPanel()
            }
        }
    }

    private fun adjustMusicVolume(increase: Boolean) {
        val step = 1
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = if (increase) (current + step).coerceAtMost(max) else (current - step).coerceAtLeast(0)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_PLAY_SOUND)
    }

    private fun toggleMusicMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            if (isMuted) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            }
        } else {
            // Older APIs fallback
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Điều khiển âm lượng"
            val descriptionText = "Hỗ trợ thay đổi âm lượng nhanh và hiển thị bảng trượt"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startServiceInForeground() {
        val notification = buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildServiceNotification())
    }

    private fun buildServiceNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Actions intents
        val upPending = createActionPendingIntent(ACTION_VOL_UP, 1)
        val downPending = createActionPendingIntent(ACTION_VOL_DOWN, 2)
        val mutePending = createActionPendingIntent(ACTION_MUTE_TOGGLE, 3)
        val showPending = createActionPendingIntent(ACTION_SHOW_OVERLAY, 4)

        val musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val musicPercentage = ((musicVolume.toFloat() / musicMax.toFloat()) * 100).toInt()

        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            musicVolume == 0
        }

        val muteLabel = if (isMuted) "Bật âm 🔊" else "Tắt âm 🔇"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Điều Khiển Âm Lượng 🔊")
            .setContentText("Âm lượng nhạc hiện tại: $musicPercentage% | Nhấn để mở cài đặt")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "Giảm -", downPending)
            .addAction(android.R.drawable.ic_media_next, "Tăng +", upPending)
            .addAction(android.R.drawable.ic_lock_silent_mode, muteLabel, mutePending)
            .addAction(android.R.drawable.ic_menu_view, "Bảng nổi ✨", showPending)
            .build()
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, VolumeControlService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ----------------- FLOATING OVERLAYS (WINDOW MANAGER) -----------------

    private fun setupFloatingHandle() {
        // Remove existing handle if any
        removeFloatingHandle()

        if (!settings.isHandleEnabled || !android.provider.Settings.canDrawOverlays(this)) {
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (settings.handleSide == "LEFT") Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = settings.handleYOffset
        }

        val context = this
        val composeView = ComposeView(context).apply {
            val lifecycleOwner = ServiceLifecycleOwner().apply {
                onCreate()
                onStart()
                onResume()
            }
            floatingHandleLifecycleOwner = lifecycleOwner
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            
            setContent {
                MaterialTheme {
                    FloatingHandleUi(
                        opacity = settings.handleOpacity,
                        heightDp = settings.handleHeight,
                        side = settings.handleSide,
                        dragAction = settings.handleDragAction,
                        onTap = {
                            showOverlayPanel()
                        },
                        onVerticalDrag = { deltaY ->
                            params.y = params.y + deltaY.toInt()
                            try {
                                windowManager.updateViewLayout(this@apply, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onDragEnd = {
                            settings.handleYOffset = params.y
                        },
                        onVolumeAdjust = { increase ->
                            adjustMusicVolume(increase)
                        }
                    )
                }
            }
        }

        floatingHandleView = composeView

        try {
            windowManager.addView(floatingHandleView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingHandle() {
        floatingHandleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingHandleView = null
        }
        floatingHandleLifecycleOwner?.let {
            it.onPause()
            it.onStop()
            it.onDestroy()
            floatingHandleLifecycleOwner = null
        }
    }

    private fun showOverlayPanel() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("request_overlay_permission", true)
            }
            startActivity(intent)
            return
        }

        if (overlayPanelView != null) return

        // Hide floating handle while panel is active
        removeFloatingHandle()
        _isOverlayVisible.value = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val context = this
        val composeView = ComposeView(context).apply {
            val lifecycleOwner = ServiceLifecycleOwner().apply {
                onCreate()
                onStart()
                onResume()
            }
            overlayPanelLifecycleOwner = lifecycleOwner
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    visible = true
                }
                
                MaterialTheme {
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInHorizontally(
                            initialOffsetX = { if (settings.handleSide == "LEFT") -it else it },
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeIn(),
                        exit = slideOutHorizontally(
                            targetOffsetX = { if (settings.handleSide == "LEFT") -it else it },
                            animationSpec = tween(durationMillis = 250)
                        ) + fadeOut()
                    ) {
                        OverlayPanelUi(
                            audioManager = audioManager,
                            database = database,
                            settings = settings,
                            onClose = {
                                visible = false
                                serviceScope.launch {
                                    delay(200) // wait for exit animation
                                    dismissOverlayPanel()
                                }
                            }
                        )
                    }
                }
            }
        }

        overlayPanelView = composeView

        try {
            windowManager.addView(overlayPanelView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissOverlayPanel() {
        overlayPanelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayPanelView = null
        }
        overlayPanelLifecycleOwner?.let {
            it.onPause()
            it.onStop()
            it.onDestroy()
            overlayPanelLifecycleOwner = null
        }
        _isOverlayVisible.value = false
        setupFloatingHandle() // Restore edge handle
    }

    override fun onDestroy() {
        _isRunning.value = false
        removeFloatingHandle()
        dismissOverlayPanel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// ---------------------- JETPACK COMPOSE OVERLAY COMPOSABLES ----------------------

@Composable
fun FloatingHandleUi(
    opacity: Float,
    heightDp: Int,
    side: String,
    dragAction: String,
    onTap: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onVolumeAdjust: (Boolean) -> Unit
) {
    var accumulatedDragY by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(heightDp.dp)
            .clip(
                if (side == "LEFT") RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .background(
                Brush.horizontalGradient(
                    colors = if (side == "LEFT") listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = opacity),
                        MaterialTheme.colorScheme.primary.copy(alpha = opacity * 0.4f)
                    ) else listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = opacity * 0.4f),
                        MaterialTheme.colorScheme.primary.copy(alpha = opacity)
                    )
                )
            )
            .pointerInput(dragAction) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDragY = 0f
                    },
                    onDragEnd = {
                        if (dragAction == "MOVE") {
                            onDragEnd()
                        }
                    },
                    onDragCancel = {
                        if (dragAction == "MOVE") {
                            onDragEnd()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAction == "VOLUME") {
                            accumulatedDragY += dragAmount.y
                            // 32f pixels of vertical drag corresponds to 1 volume step (smooth and responsive)
                            val threshold = 32f
                            if (accumulatedDragY <= -threshold) {
                                onVolumeAdjust(true) // drag up -> increase music volume
                                accumulatedDragY = 0f
                            } else if (accumulatedDragY >= threshold) {
                                onVolumeAdjust(false) // drag down -> decrease music volume
                                accumulatedDragY = 0f
                            }
                        } else {
                            onVerticalDrag(dragAmount.y)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle vertical ripple bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((heightDp * 0.5f).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.8f))
        )
    }
}

@Composable
fun OverlayPanelUi(
    audioManager: AudioManager,
    database: AppDatabase,
    settings: VolumeSettings,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Track Volume Levels dynamically
    var mediaVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var ringVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_RING)) }
    var alarmVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM)) }
    var notifVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)) }

    val presetFlow = remember { database.volumePresetDao().getAllPresets() }
    val presetListState = presetFlow.collectAsState(initial = emptyList())

    // Live volume changes tracking from hardware system buttons
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    notifVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
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

    // Limits
    val mediaMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val ringMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) }
    val alarmMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) }
    val notifMax = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) }

    // Layout configuration
    val isLeft = settings.handleSide == "LEFT"
    var isCompact by remember { mutableStateOf(settings.isCompactMode) }
    
    LaunchedEffect(isCompact) {
        settings.isCompactMode = isCompact
    }

    val density = LocalDensity.current
    val handleYOffsetDp = remember(settings.handleYOffset) {
        with(density) { settings.handleYOffset.toDp() }
    }
    
    val clampedYOffsetDp = remember(handleYOffsetDp, isCompact) {
        if (isCompact) {
            handleYOffsetDp.coerceIn((-240).dp, 240.dp)
        } else {
            handleYOffsetDp.coerceIn((-80).dp, 80.dp) // shift full mode slightly so controls are closer to finger
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onClose() }, // tap outside to close
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        if (isCompact) {
            Card(
                modifier = Modifier
                    .width(108.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .offset(y = clampedYOffsetDp)
                    .clickable(enabled = false) {}, // prevent click-through closing
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E26)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Simple compact header: [Tune/Expand] and [Close]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isCompact = false },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Hiện đầy đủ",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Taller, wider sliding bar tailored to user's finger bounds
                    IosVolumeSlider(
                        percent = (mediaVolume.toFloat() / mediaMax.toFloat()) * 100f,
                        onPercentChange = { pct ->
                            val target = ((pct / 100f) * mediaMax).toInt().coerceIn(0, mediaMax)
                            if (target != mediaVolume) {
                                mediaVolume = target
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            }
                        },
                        icon = Icons.Default.MusicNote,
                        label = "Media",
                        height = 180.dp,
                        width = 62.dp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Accurate volume fraction output
                    Text(
                        text = "${((mediaVolume.toFloat() / mediaMax.toFloat()) * 100f).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Media Mute Toggle button at base
                    IconButton(
                        onClick = {
                            val target = if (mediaVolume > 0) 0 else (mediaMax / 2)
                            mediaVolume = target
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (mediaVolume > 0) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Quick Mute Media",
                            tint = if (mediaVolume > 0) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 14.dp, vertical = 24.dp)
                    .offset(y = clampedYOffsetDp)
                    .clickable(enabled = false) {}, // prevent click-through closing
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E26) // Deep Dark Slate aesthetic matches frontend skill
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Volume Icon",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Âm Lượng ✨",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { isCompact = true },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Thu gọn giao diện",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Panel",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                // Scrollable Sliders & Presets List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sliders Section
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Media
                            IosVolumeSlider(
                                percent = (mediaVolume.toFloat() / mediaMax.toFloat()) * 100f,
                                onPercentChange = { pct ->
                                    val target = ((pct / 100f) * mediaMax).toInt().coerceIn(0, mediaMax)
                                    if (target != mediaVolume) {
                                        mediaVolume = target
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                    }
                                },
                                icon = Icons.Default.MusicNote,
                                label = "Media"
                            )

                            // Ringtone
                            IosVolumeSlider(
                                percent = (ringVolume.toFloat() / ringMax.toFloat()) * 100f,
                                onPercentChange = { pct ->
                                    val target = ((pct / 100f) * ringMax).toInt().coerceIn(0, ringMax)
                                    if (target != ringVolume) {
                                        ringVolume = target
                                        audioManager.setStreamVolume(AudioManager.STREAM_RING, target, 0)
                                    }
                                },
                                icon = Icons.Default.RingVolume,
                                label = "Chuông"
                            )

                            // Notification
                            IosVolumeSlider(
                                percent = (notifVolume.toFloat() / notifMax.toFloat()) * 100f,
                                onPercentChange = { pct ->
                                    val target = ((pct / 100f) * notifMax).toInt().coerceIn(0, notifMax)
                                    if (target != notifVolume) {
                                        notifVolume = target
                                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, target, 0)
                                    }
                                },
                                icon = Icons.Default.Notifications,
                                label = "T.Báo"
                            )

                            // Alarm
                            IosVolumeSlider(
                                percent = (alarmVolume.toFloat() / alarmMax.toFloat()) * 100f,
                                onPercentChange = { pct ->
                                    val target = ((pct / 100f) * alarmMax).toInt().coerceIn(0, alarmMax)
                                    if (target != alarmVolume) {
                                        alarmVolume = target
                                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
                                    }
                                },
                                icon = Icons.Default.Alarm,
                                label = "Báo thức"
                            )
                        }
                    }

                    // Divider
                    item {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }

                    // Presets Section Header
                    item {
                        Text(
                            text = "Cấu hình nhanh ⚡",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Empty or Presets items
                    if (presetListState.value.isEmpty()) {
                        item {
                            Text(
                                text = "Chưa có cấu hình âm thanh tùy chỉnh nào",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(presetListState.value) { preset ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Update volume levels immediately
                                        val mVol = ((preset.mediaVol.toFloat() / 100f) * mediaMax).toInt()
                                        val rVol = ((preset.ringVol.toFloat() / 100f) * ringMax).toInt()
                                        val nVol = ((preset.notificationVol.toFloat() / 100f) * notifMax).toInt()
                                        val aVol = ((preset.alarmVol.toFloat() / 100f) * alarmMax).toInt()

                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mVol, AudioManager.FLAG_PLAY_SOUND)
                                        audioManager.setStreamVolume(AudioManager.STREAM_RING, rVol, 0)
                                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, nVol, 0)
                                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, aVol, 0)

                                        mediaVolume = mVol
                                        ringVolume = rVol
                                        notifVolume = nVol
                                        alarmVolume = aVol
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = preset.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Nhạc:${preset.mediaVol}% | Chuông:${preset.ringVol}% | B.Thức:${preset.alarmVol}%",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Applied",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Handle Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vị trí phím nổi:",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                settings.handleSide = "LEFT"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLeft) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                contentColor = if (isLeft) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Trái", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                settings.handleSide = "RIGHT"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isLeft) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                contentColor = if (!isLeft) MaterialTheme.colorScheme.onPrimary else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Phải", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun ServiceSliderIcon(
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
fun ServiceLocalPercentText(
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
    
    // Decouple dragging state for instantaneous local rendering on weak devices
    var isDragging by remember { mutableStateOf(false) }
    var localPercent by remember { mutableStateOf(percent) }
    
    // Sync with external percentage only when NOT actively dragging
    LaunchedEffect(percent) {
        if (!isDragging) {
            localPercent = percent
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f)) // iOS system fluid background
                .drawBehind {
                    val fillHeight = size.height * (localPercent / 100f).coerceIn(0f, 1f)
                    drawRect(
                        color = Color.White,
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
            // Icon placed nicely inside
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ServiceSliderIcon(
                    percentProvider = { localPercent },
                    icon = icon,
                    activeColor = Color.Black.copy(alpha = 0.8f),
                    inactiveColor = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(width + 12.dp)
        )
        
        ServiceLocalPercentText(
            percentProvider = { localPercent },
            color = Color.White
        )
    }
}

class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

