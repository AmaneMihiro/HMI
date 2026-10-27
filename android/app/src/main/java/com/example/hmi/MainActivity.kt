package com.example.hmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hmi.ui.theme.HMITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HMITheme {
                LedPage()
            }
        }
    }
}

@Composable
fun LedPage() {
    var led1 by remember { mutableStateOf(false) }
    var led2 by remember { mutableStateOf(false) }
    var led3 by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F5F8))
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "HMI人机交互界面",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2933),
        )
        Spacer(modifier = Modifier.height(20.dp))
        LedRow("LED 1", led1, Color(0xFFE53935)) { led1 = !led1 }
        LedRow("LED 2", led2, Color(0xFF43A047)) { led2 = !led2 }
        LedRow("LED 3", led3, Color(0xFF1E88E5)) { led3 = !led3 }
    }
}

@Composable
private fun LedRow(name: String, on: Boolean, color: Color, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(16.dp)
                .background(if (on) color else Color(0xFFD1D5DB), CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = name, fontSize = 18.sp, color = Color(0xFF1F2933))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (on) "亮" else "灭",
            fontSize = 16.sp,
            color = if (on) color else Color(0xFF9CA3AF),
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onToggle) {
            Text(text = if (on) "关闭" else "打开")
        }
    }
}
