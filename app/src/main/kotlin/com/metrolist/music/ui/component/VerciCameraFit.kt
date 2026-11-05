package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.launch

internal data class CameraFit(val diameter: Float = 0f, val x: Float = 0f, val y: Float = 0f, val gap: Float = 1f, val thickness: Float = 1.5f)

@Composable
internal fun VerciCameraFit(onDismiss: () -> Unit) {
    val diameter by rememberPreference(VerciCameraSizeKey, 0f)
    val x by rememberPreference(VerciCameraXKey, 0f)
    val y by rememberPreference(VerciCameraYKey, 0f)
    val gap by rememberPreference(VerciCameraGapKey, 1f)
    val thickness by rememberPreference(VerciCameraThicknessKey, 1.5f)
    var fit by remember(diameter, x, y, gap, thickness) { mutableStateOf(CameraFit(diameter, x, y, gap, thickness)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val attributes = window.attributes
                    attributes.layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30)
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attributes
                }
                androidx.core.view.WindowInsetsControllerCompat(window, dialogView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
            Box(Modifier.fillMaxSize()) {
                VerciEdgeNeon(playing = true, physicalEdges = true, preview = fit)
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).fillMaxWidth().fillMaxHeight(.72f).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.verci_camera_fit), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.verci_camera_fit_hint))
                    FitSlider(R.string.verci_camera_diameter, fit.diameter, 0f..48f) { fit = fit.copy(diameter = it) }
                    FitSlider(R.string.verci_camera_x, fit.x, -80f..80f) { fit = fit.copy(x = it) }
                    FitSlider(R.string.verci_camera_y, fit.y, -40f..80f) { fit = fit.copy(y = it) }
                    FitSlider(R.string.verci_camera_gap, fit.gap, 0f..12f) { fit = fit.copy(gap = it) }
                    FitSlider(R.string.verci_camera_thickness, fit.thickness, .5f..5f) { fit = fit.copy(thickness = it) }
                    Row {
                        TextButton(onClick = { fit = CameraFit() }) { Text(stringResource(R.string.verci_camera_auto)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                        TextButton(onClick = { scope.launch {
                            context.dataStore.edit { values ->
                                values[VerciCameraSizeKey] = fit.diameter; values[VerciCameraXKey] = fit.x
                                values[VerciCameraYKey] = fit.y; values[VerciCameraGapKey] = fit.gap
                                values[VerciCameraThicknessKey] = fit.thickness
                            }
                            onDismiss()
                        } }) { Text(stringResource(android.R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FitSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Text("${stringResource(label)}: ${kotlin.math.round(value * 10) / 10} dp")
    Slider(value = value.coerceIn(range), onValueChange = change, valueRange = range)
}
