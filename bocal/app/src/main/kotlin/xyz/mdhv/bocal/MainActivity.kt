package xyz.mdhv.bocal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mdhv.bocal.audio.AudioRecordEngine
import xyz.mdhv.bocal.feature.tuner.TunerScreen
import xyz.mdhv.bocal.feature.tuner.TunerViewModel
import xyz.mdhv.bocal.hyle.HylePalette
import xyz.mdhv.bocal.pitch.YinPitchDetector

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BocalTheme {
                BocalApp()
            }
        }
    }
}

@Composable
fun BocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(HylePalette.VIOLET),
            secondary = Color(HylePalette.CYAN),
            background = Color(HylePalette.FIELD_VOID),
            surface = Color(HylePalette.SURFACE_RAISED),
            onPrimary = Color(HylePalette.FIELD_VOID),
            onBackground = Color(HylePalette.INK_PURE),
            onSurface = Color(HylePalette.INK_PURE),
        ),
        content = content,
    )
}

@Composable
private fun BocalApp() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    if (!granted) {
        MicrophoneRationale(onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) })
        return
    }

    val viewModel: TunerViewModel = viewModel(
        factory = TunerViewModel.Factory(
            engine = AudioRecordEngine(),
            detector = YinPitchDetector(),
            hapticRenderer = AndroidHapticRenderer(context.applicationContext),
        ),
    )

    // Capture only while the screen is resumed — mic off the moment we leave.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.start()
                Lifecycle.Event.ON_PAUSE -> viewModel.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }

    TunerScreen(viewModel)
}

@Composable
private fun MicrophoneRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(HylePalette.FIELD_VOID))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Bocal listens so you can see your pitch.",
            color = Color(HylePalette.INK_PURE),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Everything stays on your phone. Bocal has no internet " +
                "permission — audio cannot leave the device, and nothing is " +
                "recorded unless you turn recording on.",
            color = Color(HylePalette.GREY),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(HylePalette.VIOLET),
                contentColor = Color(HylePalette.FIELD_VOID),
            ),
        ) {
            Text("Allow microphone")
        }
    }
}
