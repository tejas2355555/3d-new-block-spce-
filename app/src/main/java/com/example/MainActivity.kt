package com.example

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BlockSpaceTheme
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicPanel
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.DeepSpaceNavy
import com.example.ui.theme.EmberCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.StarGold
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoxelEmerald
import com.example.ui.theme.VoxelEmeraldDark
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private var activeWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BlockSpaceTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val activity = context as? Activity
                val snackbarHostState = remember { SnackbarHostState() }
                var webViewRef by remember { mutableStateOf<WebView?>(null) }

                // Apply immersive system bar visibility
                LaunchedEffect(uiState.isImmersive) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (uiState.isImmersive) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                // Apply landscape lock preference
                LaunchedEffect(uiState.landscapeLocked) {
                    activity?.requestedOrientation = if (uiState.landscapeLocked) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                // Keep screen awake during active gameplay
                DisposableEffect(uiState.currentScreen) {
                    if (uiState.currentScreen == GameScreen.Play) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Show status feedback via Snackbar
                LaunchedEffect(uiState.statusMessage) {
                    val msg = uiState.statusMessage ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(msg)
                    viewModel.showStatusMessage(null)
                }

                // Native SAF Backup Export / Import launchers
                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val wv = webViewRef ?: return@rememberLauncherForActivityResult
                    wv.evaluateJavascript("window.exportWorldsJson ? window.exportWorldsJson() : ''") { raw ->
                        val json = decodeJsString(raw)
                        if (json.isBlank()) {
                            viewModel.showStatusMessage("Nothing to export yet")
                            return@evaluateJavascript
                        }
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(json.toByteArray(StandardCharsets.UTF_8))
                            }
                            viewModel.showStatusMessage("Worlds exported successfully")
                        } catch (e: Exception) {
                            viewModel.showStatusMessage("Export failed: ${e.localizedMessage ?: "error"}")
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val wv = webViewRef ?: return@rememberLauncherForActivityResult
                    try {
                        val rawJson = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(StandardCharsets.UTF_8)
                            ?.use { it.readText() }
                            .orEmpty()
                        if (rawJson.isBlank()) {
                            viewModel.showStatusMessage("Selected file is empty")
                            return@rememberLauncherForActivityResult
                        }
                        val escaped = JSONObject.quote(rawJson)
                        wv.evaluateJavascript("window.importWorldsJson ? window.importWorldsJson($escaped) : false") { res ->
                            val ok = res == "true"
                            viewModel.showStatusMessage(
                                if (ok) "Worlds imported successfully" else "Invalid backup file"
                            )
                            if (ok) viewModel.setShowDeviceSheet(false)
                        }
                    } catch (e: Exception) {
                        viewModel.showStatusMessage("Import failed: ${e.localizedMessage ?: "error"}")
                    }
                }

                // Intercept Android Back button cleanly
                BackHandler {
                    when {
                        uiState.showDeviceSheet -> viewModel.setShowDeviceSheet(false)
                        uiState.showExitDialog -> viewModel.setShowExitDialog(false)
                        uiState.loadError != null -> finish()
                        else -> {
                            val wv = webViewRef
                            if (wv == null) {
                                viewModel.setShowExitDialog(true)
                            } else {
                                wv.evaluateJavascript("window.handleAndroidBack ? window.handleAndroidBack() : 'menu'") { result ->
                                    val decoded = decodeJsString(result)
                                    if (decoded != "handled") {
                                        viewModel.setShowExitDialog(true)
                                    }
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DeepSpaceNavy,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        )
                    }
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        GameWebView(
                            reloadNonce = uiState.reloadNonce,
                            isHapticsEnabled = { viewModel.uiState.value.hapticsEnabled },
                            onEngineReady = viewModel::onEngineReady,
                            onEngineError = viewModel::onEngineError,
                            onScreenChanged = viewModel::onScreenChanged,
                            onSetImmersive = viewModel::setImmersive,
                            onToggleImmersive = viewModel::toggleImmersive,
                            onWebViewCreated = { wv ->
                                webViewRef = wv
                                activeWebView = wv
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Subtle Android Options & World Backup pill on Main Menu only
                        AnimatedVisibility(
                            visible = uiState.isEngineReady &&
                                uiState.loadError == null &&
                                uiState.currentScreen == GameScreen.Menu,
                            enter = fadeIn(tween(250)),
                            exit = fadeOut(tween(150)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(12.dp)
                        ) {
                            Surface(
                                onClick = { viewModel.setShowDeviceSheet(true) },
                                shape = RoundedCornerShape(14.dp),
                                color = CosmicPanel.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, CosmicBorder),
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("android_options_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = stringResource(R.string.device_options),
                                        tint = StarGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.device_options),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Cosmic Voxel Loading Overlay while Three.js boots
                        AnimatedVisibility(
                            visible = !uiState.isEngineReady && uiState.loadError == null,
                            enter = fadeIn(),
                            exit = fadeOut(tween(350))
                        ) {
                            CosmicLoadingOverlay()
                        }

                        // Offline / Engine Load Error Recovery
                        AnimatedVisibility(
                            visible = uiState.loadError != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            OfflineErrorOverlay(
                                detail = uiState.loadError.orEmpty(),
                                onRetry = viewModel::retryLoad
                            )
                        }
                    }
                }

                if (uiState.showDeviceSheet) {
                    DeviceOptionsDialog(
                        uiState = uiState,
                        onToggleHaptics = viewModel::setHapticsEnabled,
                        onToggleLandscape = viewModel::setLandscapeLocked,
                        onToggleImmersive = viewModel::setImmersive,
                        onExport = { exportLauncher.launch("block-space-3d-worlds.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        onDismiss = { viewModel.setShowDeviceSheet(false) }
                    )
                }

                if (uiState.showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.setShowExitDialog(false) },
                        containerColor = CosmicSurface,
                        title = {
                            Text(
                                text = stringResource(R.string.exit_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = StarGold
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.exit_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.setShowExitDialog(false)
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmberCoral),
                                modifier = Modifier.testTag("exit_confirm_button")
                            ) {
                                Text(stringResource(R.string.exit_app))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.setShowExitDialog(false) },
                                modifier = Modifier.testTag("exit_cancel_button")
                            ) {
                                Text(stringResource(R.string.stay), color = SkyCyan)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        activeWebView?.evaluateJavascript("window.saveWorldNow && window.saveWorldNow()", null)
        activeWebView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        activeWebView?.onResume()
    }
}

private fun decodeJsString(raw: String?): String {
    if (raw == null || raw == "null") return ""
    return try {
        JSONObject("{\"v\":$raw}").optString("v", "")
    } catch (_: Exception) {
        raw.trim('"')
    }
}

@Composable
private fun CosmicLoadingOverlay() {
    val transition = rememberInfiniteTransition(label = "voxel_pulse")
    val bob by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1030),
                        Color(0xFF141D48),
                        Color(0xFF0A0F28)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(92.dp)
                    .testTag("loading_voxel_cube")
            ) {
                drawIsometricVoxelCube(yOffsetPx = bob)
            }
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = StarGold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.loading_universe),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.loading_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DrawScope.drawIsometricVoxelCube(yOffsetPx: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f + yOffsetPx
    val r = size.minDimension * 0.36f
    val dx = r * 0.866f
    val dy = r * 0.5f

    val top = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + dx, cy - dy)
        lineTo(cx, cy)
        lineTo(cx - dx, cy - dy)
        close()
    }
    val left = Path().apply {
        moveTo(cx - dx, cy - dy)
        lineTo(cx, cy)
        lineTo(cx, cy + r)
        lineTo(cx - dx, cy + dy)
        close()
    }
    val right = Path().apply {
        moveTo(cx + dx, cy - dy)
        lineTo(cx + dx, cy + dy)
        lineTo(cx, cy + r)
        lineTo(cx, cy)
        close()
    }

    drawPath(top, color = VoxelEmerald)
    drawPath(left, color = Color(0xFF8B5E3C))
    drawPath(right, color = Color(0xFF6B4428))
    drawCircle(
        color = StarGold.copy(alpha = 0.85f),
        radius = r * 0.14f,
        center = Offset(cx + dx * 0.45f, cy + dy * 0.3f)
    )
}

@Composable
private fun OfflineErrorOverlay(
    detail: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceNavy.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicPanel),
            border = BorderStroke(1.dp, CosmicBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = stringResource(R.string.offline_title),
                    tint = EmberCoral,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = stringResource(R.string.offline_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = StarGold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.offline_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = VoxelEmerald),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun DeviceOptionsDialog(
    uiState: GameUiState,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleLandscape: (Boolean) -> Unit,
    onToggleImmersive: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicSurface,
        title = {
            Text(
                text = stringResource(R.string.device_options),
                style = MaterialTheme.typography.titleLarge,
                color = StarGold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionSwitchRow(
                    title = stringResource(R.string.haptics_label),
                    subtitle = stringResource(R.string.haptics_desc),
                    checked = uiState.hapticsEnabled,
                    onCheckedChange = onToggleHaptics,
                    testTag = "haptics_switch"
                )
                OptionSwitchRow(
                    title = stringResource(R.string.landscape_lock_label),
                    subtitle = stringResource(R.string.landscape_lock_desc),
                    checked = uiState.landscapeLocked,
                    onCheckedChange = onToggleLandscape,
                    testTag = "landscape_lock_switch"
                )
                OptionSwitchRow(
                    title = stringResource(R.string.immersive_label),
                    subtitle = stringResource(R.string.immersive_desc),
                    checked = uiState.isImmersive,
                    onCheckedChange = onToggleImmersive,
                    testTag = "immersive_switch"
                )

                HorizontalDivider(color = CosmicBorder)

                Text(
                    text = stringResource(R.string.backup_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyCyan
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExport,
                        border = BorderStroke(1.dp, SkyCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_worlds_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = null,
                            tint = SkyCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.export_worlds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(
                        onClick = onImport,
                        border = BorderStroke(1.dp, VoxelEmerald.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("import_worlds_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = VoxelEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.import_worlds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VoxelEmeraldDark),
                modifier = Modifier.testTag("device_options_close")
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun OptionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
