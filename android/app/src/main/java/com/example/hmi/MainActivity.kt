package com.example.hmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hmi.ui.theme.HMITheme
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val POS_MIN = -180f
private const val POS_MAX = 180f

/** 按 0.1° 的整数倍加减，避免 0.1 直接相加越来越不准。 */
private fun shiftTenths(value: Float, tenths: Int): Float {
    val next = (value * 10f).roundToInt() + tenths
    return (next / 10f).coerceIn(POS_MIN, POS_MAX)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HMITheme {
                HmiPage()
            }
        }
    }
}

@Composable
fun HmiPage() {
    var led1 by remember { mutableStateOf(false) }
    var led2 by remember { mutableStateOf(false) }
    var led3 by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(0f) }
    var current by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20)
            if (current < target) {
                current = shiftTenths(current, 1).coerceAtMost(target)
            } else if (current > target) {
                current = shiftTenths(current, -1).coerceAtLeast(target)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F5F8))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text(
            text = "HMI人机交互界面",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2933),
        )
        Spacer(modifier = Modifier.height(20.dp))
        SectionCard(title = "指示灯") {
            LedRow("LED 1", led1, Color(0xFFE53935)) { led1 = !led1 }
            LedRow("LED 2", led2, Color(0xFF43A047)) { led2 = !led2 }
            LedRow("LED 3", led3, Color(0xFF1E88E5)) { led3 = !led3 }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionCard(title = "电机位置") {
            MotorPanel(
                target = target,
                current = current,
                onMinus = { target = shiftTenths(target, -1) },
                onPlus = { target = shiftTenths(target, 1) },
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2933),
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LedRow(name: String, on: Boolean, color: Color, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (on) color else Color(0xFFE5E7EB), CircleShape)
                .padding(4.dp)
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
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (on) Color(0xFFE5E7EB) else color,
                contentColor = if (on) Color(0xFF1F2933) else Color.White,
            ),
        ) {
            Text(text = if (on) "关闭" else "打开")
        }
    }
}

@Composable
private fun MotorPanel(
    target: Float,
    current: Float,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val arrived = current == target
    val fraction = ((current - POS_MIN) / (POS_MAX - POS_MIN)).coerceIn(0f, 1f)

    Row(modifier = Modifier.fillMaxWidth()) {
        PositionBox(
            label = "目标位置",
            value = target,
            valueColor = Color(0xFF1E88E5),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        PositionBox(
            label = "当前位置",
            value = current,
            valueColor = if (arrived) Color(0xFF43A047) else Color(0xFFFB8C00),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(Color(0xFFE5E7EB), RoundedCornerShape(5.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Color(0xFF1E88E5), RoundedCornerShape(5.dp)),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = if (arrived) "已到达目标位置" else "正在前往目标位置",
        fontSize = 14.sp,
        color = if (arrived) Color(0xFF43A047) else Color(0xFFFB8C00),
    )
    Spacer(modifier = Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        HoldRepeatButton(
            text = "位置 −",
            enabled = target > POS_MIN,
            color = Color(0xFF546E7A),
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            onStep = onMinus,
        )
        Spacer(modifier = Modifier.width(12.dp))
        HoldRepeatButton(
            text = "位置 +",
            enabled = target < POS_MAX,
            color = Color(0xFF1E88E5),
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            onStep = onPlus,
        )
    }
}

@Composable
private fun HoldRepeatButton(
    text: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onStep: () -> Unit,
) {
    var holding by remember { mutableStateOf(false) }
    val background = when {
        !enabled -> Color(0xFFD1D5DB)
        holding -> color.copy(alpha = 0.75f)
        else -> color
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(26.dp))
            .pointerInput(enabled) {
                if (!enabled) {
                    return@pointerInput
                }
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        down.consume()
                        holding = true
                        val repeat = this@coroutineScope.launch {
                            onStep()
                            delay(80)
                            while (isActive) {
                                onStep()
                                delay(16)
                            }
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) {
                                    break
                                }
                            }
                        } finally {
                            repeat.cancel()
                            holding = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            color = if (enabled) Color.White else Color(0xFF6B7280),
        )
    }
}

@Composable
private fun PositionBox(
    label: String,
    value: Float,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFFF3F5F8), RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 14.sp, color = Color(0xFF6B7280))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatDegree(value),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

private fun formatDegree(value: Float): String {
    val tenths = (value * 10f).roundToInt()
    val sign = if (tenths < 0) "-" else ""
    val abs = kotlin.math.abs(tenths)
    return "$sign${abs / 10}.${abs % 10}°"
}
