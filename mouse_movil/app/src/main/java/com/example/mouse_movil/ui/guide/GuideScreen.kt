package com.example.mouse_movil.ui.guide

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mouse_movil.data.SettingsRepository
import kotlinx.coroutines.launch

private data class GuidePageSpec(
    val pageIndex: Int,
    val title: String,
    val subtitle: String,
    val bullets: List<String>
)

private val GuidePages = listOf(
    GuidePageSpec(
        0,
        "1. Conectar con tu PC Windows 11",
        "Tres formas rápidas en la misma red Wi-Fi.",
        listOf(
            "Escaneo QR (recomendado): Abre el panel MSE en la bandeja de tu PC y escanea el código QR para vincular y fijar el certificado TLS en un solo paso.",
            "Detección Automática (UDP): Toca tu PC en la lista detectada, comprueba que el código XXXX-XXXX coincida con el de la PC y escribe el PIN de 6 dígitos.",
            "IP Manual: Introduce la IP local y puerto (47800) si tu router bloquea paquetes broadcast."
        )
    ),
    GuidePageSpec(
        1,
        "2. Gestos con 1 dedo",
        "Control fluido del cursor y arrastre.",
        listOf(
            "Deslizar 1 dedo: Mueve el cursor con aceleración dinámica.",
            "1 toque rápido: Clic izquierdo.",
            "Doble toque: Doble clic izquierdo para abrir carpetas o programas.",
            "Mantener presionado 400 ms (sentirás una vibración) y mover: Arrastrar ventanas o archivos (Drag & Drop)."
        )
    ),
    GuidePageSpec(
        2,
        "3. Gestos con 2 dedos",
        "Clic derecho, desplazamiento y zoom.",
        listOf(
            "Toque con 2 dedos: Clic derecho (abre el menú contextual de Windows).",
            "Deslizar 2 dedos en paralelo: Scroll vertical u horizontal suave.",
            "Pellizcar / separar 2 dedos: Zoom (envía Ctrl + Rueda a la ventana activa)."
        )
    ),
    GuidePageSpec(
        3,
        "4. Gestos con 3 dedos",
        "Clic central instantáneo.",
        listOf(
            "Toque con 3 dedos simultáneos: Clic central (rueda del mouse).",
            "Ideal para abrir enlaces en una pestaña nueva o cerrar pestañas en el navegador."
        )
    ),
    GuidePageSpec(
        4,
        "5. Teclado en tiempo real",
        "Escritura Unicode y modificadores sticky.",
        listOf(
            "Toca «Teclado» en la barra inferior del trackpad para escribir en tiempo real en la PC (soporta acentos, ñ y emojis).",
            "Barra de teclas especiales: Esc, Tab, flechas, Supr, Inicio, Fin y F1–F12.",
            "Modificadores Sticky (Ctrl, Alt, Shift, Win): Actívalos con un toque y pulsa cualquier letra para enviar combinaciones como Ctrl+F o Win+R."
        )
    ),
    GuidePageSpec(
        5,
        "6. Multimedia y atajos rápidos",
        "Volumen, reproducción y energía.",
        listOf(
            "Toca «Controles» para subir/bajar volumen, silenciar o controlar tu música y videos (Play/Pausa, Siguiente, Anterior).",
            "Atajos rápidos integrados: Alt+Tab, Copiar, Pegar, Cortar, Deshacer y Mostrar escritorio.",
            "Acciones de sistema: Bloquear sesión con un toque, o Suspender, Reiniciar y Apagar la PC (con confirmación de seguridad)."
        )
    ),
    GuidePageSpec(
        6,
        "7. Atajos personalizados",
        "Tus propias macros a un toque.",
        listOf(
            "Abre el Editor de Atajos desde el panel de Controles o Configuración.",
            "Elige un nombre, un ícono, los modificadores (Ctrl/Alt/Shift/Win) y la tecla principal.",
            "Usa el botón «Probar» para ejecutar la combinación en vivo y ordénalos con las flechas."
        )
    ),
    GuidePageSpec(
        7,
        "8. Seguridad y reconexión",
        "Tokens protegidos y verificación TLS.",
        listOf(
            "Una vez emparejado, tu teléfono guarda un token cifrado en el Android Keystore y se reconecta automáticamente al abrir la app.",
            "Si la IP de tu PC cambia por DHCP, la app la redescubre automáticamente por su huella criptográfica.",
            "Si cambias o reinstalas la PC y su certificado cambia, la app te avisará para evitar suplantaciones en la red Wi-Fi."
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    settingsRepository: SettingsRepository,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { GuidePages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settingsRepository.markFirstLaunchGuideSeen()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guía de Uso (${pagerState.currentPage + 1}/${GuidePages.size})") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar guía")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIdx ->
                val page = GuidePages[pageIdx]
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GestureIllustrationCanvas(
                            pageIndex = page.pageIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        )

                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        for (b in page.bullets) {
                            Text(
                                text = "• $b",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Page indicators + Next / Done buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = pagerState.currentPage > 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                ) {
                    Text("Anterior")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(GuidePages.size) { idx ->
                        val active = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(if (active) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < GuidePages.lastIndex) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onClose()
                        }
                    }
                ) {
                    Text(if (pagerState.currentPage < GuidePages.lastIndex) "Siguiente" else "Empezar")
                }
            }
        }
    }
}

@Composable
private fun GestureIllustrationCanvas(
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "guide_anim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "finger_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = 18.dp.toPx()

        when (pageIndex) {
            0 -> {
                // QR / PC connection illustration
                val boxSize = 72.dp.toPx()
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(cx - boxSize * 0.5f, cy - boxSize * 0.5f),
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
                val scanY = (cy - boxSize * 0.4f) + boxSize * 0.8f * progress
                drawLine(
                    color = secondaryColor,
                    start = Offset(cx - boxSize * 0.45f, scanY),
                    end = Offset(cx + boxSize * 0.45f, scanY),
                    strokeWidth = 3.dp.toPx()
                )
            }
            1 -> {
                // 1 finger moving across trackpad
                val start = Offset(cx - 60.dp.toPx(), cy + 20.dp.toPx())
                val end = Offset(cx + 60.dp.toPx(), cy - 20.dp.toPx())
                val cur = Offset(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.35f),
                    start = start,
                    end = cur,
                    strokeWidth = 4.dp.toPx()
                )
                drawFingerCircle(cur, r, primaryColor)
            }
            2 -> {
                // 2 fingers scrolling / pinching
                val offsetY = (progress - 0.5f) * 50.dp.toPx()
                val p1 = Offset(cx - 28.dp.toPx(), cy + offsetY)
                val p2 = Offset(cx + 28.dp.toPx(), cy + offsetY)
                drawFingerCircle(p1, r, primaryColor)
                drawFingerCircle(p2, r, primaryColor)
            }
            3 -> {
                // 3 fingers tap
                val pulse = 0.85f + 0.3f * progress
                val y = cy
                drawFingerCircle(Offset(cx - 42.dp.toPx(), y + 6.dp.toPx()), r * pulse, primaryColor)
                drawFingerCircle(Offset(cx, y - 8.dp.toPx()), r * pulse, primaryColor)
                drawFingerCircle(Offset(cx + 42.dp.toPx(), y + 6.dp.toPx()), r * pulse, primaryColor)
            }
            else -> {
                // Keyboard / Shortcuts / Security badge pulse
                val radius = 26.dp.toPx() * (0.9f + 0.2f * progress)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.22f),
                    radius = radius * 1.4f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = primaryColor,
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFingerCircle(
    center: Offset,
    radius: Float,
    color: Color
) {
    drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = radius * 1.35f,
        center = center
    )
    drawCircle(
        color = color,
        radius = radius,
        center = center
    )
}
