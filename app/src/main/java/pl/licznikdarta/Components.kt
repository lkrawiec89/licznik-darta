package pl.licznikdarta

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

enum class MainTab(val label: String, val icon: ImageVector) {
    START("Start", Icons.Default.Home),
    PLAYERS("Gracze", Icons.Default.People),
    STATS("Statystyka", Icons.Default.BarChart),
    HISTORY("Wszystkie gry", Icons.Default.History),
    SETTINGS("Ustawienia", Icons.Default.Settings)
}

/**
 * Autentyczna ikona lotki (dart) rysowana na Canvas pod katem 45 stopni
 * (grot u góry po lewej, piórka na dole po prawej).
 */
@Composable
fun DartIcon(
    modifier: Modifier = Modifier,
    color: Color = DartGreen,
    needleColor: Color = Color(0xFFC7C7CC)
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // P0 (czubek igły) -> P1 (początek korpusu)
        val p0 = Offset(w * 0.12f, h * 0.12f)
        val p1 = Offset(w * 0.32f, h * 0.32f)
        // P1 -> P2 (baryłka/barrel)
        val p2 = Offset(w * 0.54f, h * 0.54f)
        // P2 -> P3 (shafty/trzon)
        val p3 = Offset(w * 0.72f, h * 0.72f)
        // P3 -> P4 (koniec piórek)
        val p4 = Offset(w * 0.90f, h * 0.90f)

        // Igła
        drawLine(
            color = needleColor,
            start = p0,
            end = p1,
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        // Baryłka
        drawLine(
            color = Color(0xFF7C7C80),
            start = p1,
            end = p2,
            strokeWidth = 5.5f,
            cap = StrokeCap.Round
        )

        // Trzon (shaft)
        drawLine(
            color = Color(0xFF3A3A3C),
            start = p2,
            end = p4,
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )

        // Piórko (flight) - trójkątne skrzydła
        val dx = p4.x - p3.x
        val dy = p4.y - p3.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len * 6f
        val ny = dx / len * 6f

        val wing1 = Path().apply {
            moveTo(p3.x, p3.y)
            lineTo(p4.x + nx, p4.y + ny)
            lineTo(p4.x, p4.y)
            close()
        }
        val wing2 = Path().apply {
            moveTo(p3.x, p3.y)
            lineTo(p4.x - nx, p4.y - ny)
            lineTo(p4.x, p4.y)
            close()
        }

        drawPath(wing1, color = color)
        drawPath(wing2, color = color)
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Wstecz",
                    tint = DartGreen,
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
            textAlign = if (onBack != null) TextAlign.Start else TextAlign.Center
        )

        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        } else if (onBack != null) {
            Spacer(Modifier.width(48.dp))
        }
    }
}

/**
 * Segmented control (np. [ X01 ] [ Cricket ]) w stylu screenów
 */
@Composable
fun SegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF141414),
        border = androidx.compose.foundation.BorderStroke(1.dp, DartGreen)
    ) {
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, text ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) DartGreen else Color.Transparent)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else Color.White
                    )
                }
                if (index < items.size - 1) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(DartGreen)
                    )
                }
            }
        }
    }
}

/**
 * Przycisk opcji z etykietą u góry (np. "Punkty" -> "On", "Tryb" -> "Normal")
 */
@Composable
fun GameOptionButton(
    label: String,
    value: String,
    color: Color = DartGreen,
    textColor: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = DartSubtle,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = color
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

/**
 * Wielki czerwony przycisk START
 */
@Composable
fun RedStartButton(
    text: String = "START",
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) DartRed else DartRed.copy(alpha = 0.4f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Dolny pasek nawigacji (5 zakładek: Start, Gracze, Statystyka, Wszystkie gry, Ustawienia)
 */
@Composable
fun MainBottomBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF141414),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF242424))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val active = tab == selectedTab
                val tint = if (active) DartGreen else DartSubtle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectTab(tab) }
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = tint
                    )
                }
            }
        }
    }
}

/**
 * Klawisz klawiatury rzutów
 */
@Composable
fun KeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    color: Color = Color.Transparent,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null
) {
    val baseBg = if (color != Color.Transparent) color else DartKeypadBg
    val finalBg = if (enabled) baseBg else DartKeypadBg.copy(alpha = 0.35f)
    val finalTextColor = if (enabled) textColor else DartSubtle.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .height(52.dp)
            .padding(1.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(6.dp),
        color = finalBg,
        border = if (active) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = if (text.length > 3) 14.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                color = finalTextColor,
                textDecoration = textDecoration
            )
        }
    }
}

@Composable
fun MenuButton(text: String, subtitle: String? = null, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = DartCardBg,
        enabled = enabled
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = DartSubtle)
        }
    }
}

@Composable
fun PlayerChip(name: String, active: Boolean, detail: String? = null, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.padding(3.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp),
        color = if (active) DartGreen else DartCardBg,
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, DartOutline)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                name,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) Color.Black else Color.White
            )
            if (detail != null) Text(
                detail,
                fontSize = 12.sp,
                color = if (active) Color.Black.copy(alpha = 0.8f) else DartSubtle
            )
        }
    }
}

@Composable
fun BigNumber(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 54.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = modifier, color = Color.White)
}

@Composable
fun TurnDots(darts: Int, max: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(max) { i ->
            Box(
                Modifier.size(10.dp)
                    .background(
                        if (i < darts) DartGreen else Color(0xFF3A3A3C),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 14.sp, color = DartSubtle, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
