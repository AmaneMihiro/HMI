package com.example.hmi

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.hmi.ui.theme.HMITheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val link = HmiLink(this)
        setContent {
            HMITheme {
                HmiScreen(link)
            }
        }
    }
}

@Composable
private fun HmiScreen(link: HmiLink) {
    var state by remember { mutableStateOf(HmiState()) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            link.startScan()
        } else {
            link.showStatus("需要蓝牙权限")
        }
    }

    DisposableEffect(link) {
        link.setListener { state = it }
        onDispose { link.close() }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("HMI", style = MaterialTheme.typography.headlineMedium)
            Text(state.status, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    permission.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                        )
                    )
                }) {
                    Text(if (state.scanning) "扫描中" else "扫描")
                }
                Button(onClick = { link.disconnect() }, enabled = state.connected) {
                    Text("断开")
                }
            }
            state.devices.forEach { device ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { link.connect(device) }
                        .padding(vertical = 6.dp),
                ) {
                    Text(device.name)
                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Text("姿态", style = MaterialTheme.typography.titleMedium)
            AngleRow("Roll", state.attitudeValid, state.roll)
            AngleRow("Pitch", state.attitudeValid, state.pitch)
            AngleRow("Yaw", state.attitudeValid, state.yaw)
            HorizontalDivider()
            HoldRow("蜂鸣器", state.buzzer, state.connected) { link.setBuzzer(it) }
            ControlRow("LED", state.led, state.connected) { link.setLed(it) }
        }
    }
}

@Composable
private fun AngleRow(label: String, valid: Boolean, degrees: Float) {
    val text = if (valid) String.format(Locale.US, "%7.2f°", degrees) else "   --"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HoldRow(
    title: String,
    held: Boolean,
    enabled: Boolean,
    onHold: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Surface(
            color = if (held) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            return@detectTapGestures
                        }
                        onHold(true)
                        tryAwaitRelease()
                        onHold(false)
                    },
                )
            },
        ) {
            Text(
                "响",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (held) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ControlRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
