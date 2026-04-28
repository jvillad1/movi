package com.jvillada.movi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlin.math.roundToInt

@Composable
fun OnboardingDots(step: Int, total: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until total).forEach { i ->
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .width(if (i == step - 1) 18.dp else 6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        when {
                            i < step -> MinPrimary
                            else -> MinSurfaceContainerHigh
                        }
                    )
            )
        }
    }
}

@Composable
fun MinPrimaryButton(
    text: String,
    dark: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (dark) MinSurfaceContainerHigh else MinPrimaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (dark) MinText else MinOnPrimaryContainer,
            letterSpacing = 0.1.sp,
        )
    }
}

@Composable
fun WelcomeScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnboardingDots(step = 1)
            Text("Saltar", fontSize = 13.sp, color = MinTextMute, modifier = Modifier.clickable { onNavigate(Screen.Dashboard) })
        }

        // Visual — stacked cards
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Back card — rotated
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 0.dp, y = 30.dp)
                    .width(220.dp)
                    .rotate(-5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinSurfaceContainerLow)
                    .padding(18.dp),
            ) {
                Column {
                    Text("Familiar", fontSize = 11.sp, color = MinTextMute)
                    Spacer(Modifier.height(6.dp))
                    Text("$4.870.000", fontSize = 22.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-0.6).sp)
                    Spacer(Modifier.height(12.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("3 miembros", fontSize = 11.sp, color = MinTextMute)
                        Text("+12,4%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MinTextMute)
                    }
                }
            }

            // Front card — rotated
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 0.dp, y = (-40).dp)
                    .width(240.dp)
                    .rotate(4f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinSurfaceContainerHigh)
                    .padding(20.dp),
            ) {
                Column {
                    Text("Balance · abril", fontSize = 11.sp, color = MinTextMute)
                    Spacer(Modifier.height(6.dp))
                    Text("$1.840.000", fontSize = 28.sp, fontFamily = FontFamily.Monospace, color = MinText, letterSpacing = (-1.0).sp)
                    Spacer(Modifier.height(10.dp))
                    SimpleSparkline(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = "MOVI",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                color = MinTextMute,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Tu plata, en orden.",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-1.0).sp,
                lineHeight = 36.sp,
            )
            Text(
                text = "Sin esfuerzo.",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = MinTextMute,
                letterSpacing = (-1.0).sp,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Movi lee tus SMS, extractos y recibos para que veas a dónde se va cada peso — tuyo y de tu familia.",
                fontSize = 14.sp,
                color = MinTextDim,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(28.dp))
            MinPrimaryButton("Empezar ahora") { onNavigate(Screen.OnboardingProfile) }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "¿Ya tienes cuenta? Iniciar sesión",
                fontSize = 13.sp,
                color = MinTextMute,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        NavPill()
    }
}

@Composable
fun OnboardingProfileScreen(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("‹", fontSize = 22.sp, color = MinText)
            OnboardingDots(step = 6)
            Spacer(Modifier.weight(1f))
            Text("6/6", fontSize = 12.sp, color = MinTextMute, fontFamily = FontFamily.Monospace)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            Text(
                text = "ÚLTIMO PASO",
                fontSize = 11.sp,
                color = MinTextMute,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Cuéntanos cómo te relacionas con la plata",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MinText,
                letterSpacing = (-0.7).sp,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Esto nos ayuda a darte recomendaciones que sí encajen contigo. Puedes cambiarlo cuando quieras.",
                fontSize = 13.5.sp,
                color = MinTextDim,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(28.dp))
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(22.dp),
            ) {
                SliderQuestion("¿Qué tan cómoda te resulta la incertidumbre?", 3, "Prefiero seguridad", "Acepto volatilidad")
                Spacer(Modifier.height(26.dp))
                SliderQuestion("¿Qué tan disponible necesitas tu plata?", 4, "Puede estar lejos", "Disponible siempre")
                Spacer(Modifier.height(26.dp))
                SliderQuestion("¿Cómo te sientes con los créditos?", 2, "Los evito", "Son herramienta")
                Spacer(Modifier.height(26.dp))
                SliderQuestion("¿Qué tanto sabes de inversiones?", 3, "Empezando", "Manejo varios productos")
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            MinPrimaryButton("Crear mi perfil") { onNavigate(Screen.Dashboard) }
        }
        NavPill()
    }
}

@Composable
private fun SliderQuestion(
    label: String,
    defaultValue: Int = 3,
    leftHint: String,
    rightHint: String,
    max: Int = 5,
) {
    var value by remember { mutableStateOf(defaultValue) }
    var trackWidthPx by remember { mutableStateOf(0f) }

    fun updateFromX(x: Float) {
        if (trackWidthPx > 0f) {
            val p = (x / trackWidthPx).coerceIn(0f, 1f)
            value = (p * (max - 1)).roundToInt() + 1
        }
    }

    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinText, letterSpacing = (-0.1).sp)
        Spacer(Modifier.height(14.dp))

        val pct = (value - 1).toFloat() / (max - 1)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(max) { detectTapGestures { updateFromX(it.x) } }
                .pointerInput(max) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinSurfaceContainerHigh)
            )
            if (pct > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MinPrimary)
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterStart)
                    .offset {
                        val thumbPx = 20.dp.roundToPx()
                        val x = (pct * (trackWidthPx - thumbPx)).coerceAtLeast(0f).toInt()
                        IntOffset(x, 0)
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(MinPrimary)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(leftHint, fontSize = 11.5.sp, color = MinTextMute)
            Text(rightHint, fontSize = 11.5.sp, color = MinTextMute)
        }
    }
}
