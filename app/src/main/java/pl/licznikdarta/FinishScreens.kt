package pl.licznikdarta

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

data class FinishData(
    val type: GameType,
    val players: List<Player>,
    val finishedOrder: List<Int>,
    val perfs: List<Perf>,
    val summary: String
)

fun finishDataFor(engine: Any): FinishData = when (engine) {
    is X01Engine -> FinishData(GameType.X01, engine.players, engine.finishedOrder.toList(),
        engine.players.indices.map { engine.perf(it) },
        buildString {
            append("X01 " + engine.startScore + " (" + engine.checkOut.pl + ")\n")
            engine.finishedOrder.forEachIndexed { place, idx ->
                val pName = engine.players.getOrNull(idx)?.name ?: ""
                val sWon = engine.setsWon.getOrElse(idx) { 0 }
                val lWon = engine.legsWonTotal.getOrElse(idx) { 0 }
                append("${place + 1}. $pName — sety $sWon, legi $lWon\n")
            }
        })
    is CricketEngine -> FinishData(GameType.CRICKET, engine.players, engine.finishedOrder.toList(),
        engine.players.indices.map { engine.perf(it) },
        buildString {
            append("Cricket (" + (if (engine.pointsOn) "punkty" else "bez punktów"))
            if (engine.cutThroat) append(", Cut Throat")
            append(")\n")
            engine.finishedOrder.forEachIndexed { place, idx ->
                val pName = engine.players.getOrNull(idx)?.name ?: ""
                val sWon = engine.setsWon.getOrElse(idx) { 0 }
                val lWon = engine.legsWonTotal.getOrElse(idx) { 0 }
                append("${place + 1}. $pName — sety $sWon, legi $lWon\n")
            }
        })
    is AroundEngine -> {
        val order = engine.players.indices.sortedWith(
            compareByDescending<Int> { if (it == engine.winnerIdx) 1 else 0 }
                .thenByDescending { engine.targets.getOrElse(it) { 0 } }
                .thenBy { engine.throwsPerPlayer.getOrElse(it) { 0 } }
        )
        FinishData(GameType.AROUND, engine.players, order,
            engine.players.indices.map { engine.perf(it) },
            buildString {
                append("Dookoła tarczy\n")
                order.forEachIndexed { place, idx ->
                    val pName = engine.players.getOrNull(idx)?.name ?: ""
                    val tSec = engine.targets.getOrElse(idx) { 0 }
                    append("${place + 1}. $pName — sektor $tSec\n")
                }
            })
    }
    else -> FinishData(GameType.X01, emptyList(), emptyList(), emptyList(), "")
}

@Composable
fun FinishScreen(
    data: FinishData,
    state: AppState,
    audio: GameAudio? = null,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onStatistics: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Wyniki, 1 = Statystyki

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KONIEC GRY", fontSize = 16.sp, color = DartGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (data.finishedOrder.isNotEmpty() && data.finishedOrder[0] < data.players.size) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Wygrywa ${data.players[data.finishedOrder[0]].name}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (audio != null) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { audio.playVictory() }) {
                        Text("🎵", fontSize = 22.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Zakładki: Wyniki | Statystyki
        SegmentedTabs(
            items = listOf("Wyniki", "Statystyki"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            // Zakładka WYNIKI
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(data.finishedOrder) { place, idx ->
                    if (idx < data.players.size) {
                        val p = data.players[idx]
                        val perf = data.perfs.getOrNull(idx) ?: Perf()
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (place == 0) DartGreen.copy(alpha = 0.18f) else DartCardBg
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${place + 1}.",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (place == 0) DartGreen else DartSubtle
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    when (data.type) {
                                        GameType.X01 -> Text(
                                            "śr %.1f · legi %d · najlepsza tura %d".format(
                                                if (perf.throws > 0) perf.points.toDouble() / perf.throws * 3 else 0.0,
                                                perf.legsWon,
                                                perf.bestTurn
                                            ),
                                            fontSize = 12.sp,
                                            color = DartSubtle
                                        )
                                        GameType.CRICKET -> Text(
                                            "MPR %.2f · legi %d".format(
                                                if (perf.throws > 0) perf.marks.toDouble() / perf.throws * 3 else 0.0,
                                                perf.legsWon
                                            ),
                                            fontSize = 12.sp,
                                            color = DartSubtle
                                        )
                                        GameType.AROUND -> Text(
                                            "sektor ${perf.points} · rzutów ${perf.throws}",
                                            fontSize = 12.sp,
                                            color = DartSubtle
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Zakładka STATYSTYKI (Bezpośrednio na ekranie - nigdy nie znika!)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                data.players.indices.forEach { i ->
                    val p = data.players[i]
                    val perf = data.perfs.getOrNull(i) ?: Perf()
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = DartCardBg
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DartGreen)
                            Spacer(Modifier.height(6.dp))
                            when (data.type) {
                                GameType.X01 -> {
                                    StatRow("Średnia 3-lotkowa", "%.1f".format(if (perf.throws > 0) perf.points.toDouble() / perf.throws * 3 else 0.0))
                                    StatRow("First 9", "%.1f".format(if (perf.throws > 0) perf.first9 / 3.0 else 0.0))
                                    StatRow("Najlepsza tura", perf.bestTurn.toString())
                                    StatRow("Rzuty", perf.throws.toString())
                                    StatRow("Podwójne", perf.doubles.toString())
                                    StatRow("Potrójne", perf.triples.toString())
                                    StatRow("Najwyższy checkout", if (perf.maxCheckout > 0) perf.maxCheckout.toString() else "—")
                                    StatRow("Min lotek na lega", if (perf.minDartsLeg > 0) perf.minDartsLeg.toString() else "—")
                                    StatRow("Legi wygrane", perf.legsWon.toString())
                                }
                                GameType.CRICKET -> {
                                    StatRow("MPR", "%.2f".format(if (perf.throws > 0) perf.marks.toDouble() / perf.throws * 3 else 0.0))
                                    StatRow("Marks", perf.marks.toString())
                                    StatRow("Rzuty", perf.throws.toString())
                                    StatRow("Podwójne", perf.doubles.toString())
                                    StatRow("Potrójne", perf.triples.toString())
                                    StatRow("Legi wygrane", perf.legsWon.toString())
                                }
                                GameType.AROUND -> {
                                    StatRow("Sektor", perf.points.toString())
                                    StatRow("Rzuty", perf.throws.toString())
                                }
                            }
                            if (perf.hitsBySector.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Trafienia w sektor:",
                                    fontSize = 12.sp,
                                    color = DartSubtle
                                )
                                Text(
                                    perf.hitsBySector.entries
                                        .sortedByDescending { it.value }
                                        .take(10)
                                        .joinToString("  ") { "${it.key}:${it.value}" },
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Przyciski akcji
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                Text("Cofnij rzut", fontSize = 12.sp, color = Color.White)
            }
            OutlinedButton(onClick = onDontSave, modifier = Modifier.weight(1f)) {
                Text("Nie zapisuj", fontSize = 12.sp, color = DartRed)
            }
            OutlinedButton(
                onClick = {
                    selectedTab = if (selectedTab == 0) 1 else 0
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (selectedTab == 0) "Statystyki" else "Wyniki",
                    fontSize = 12.sp,
                    color = DartGreen
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
        ) {
            Text("ZAPISZ GRĘ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

/** Wykres: pozostaly wynik po kazdej lotce (ostatnia lega, X01). */
@Composable
private fun X01Chart(engine: X01Engine, playerIdx: Int, modifier: Modifier) {
    val pts = remember(engine.turnsLog.size) {
        try {
            engine.perfChartPoints(playerIdx)
        } catch (_: Exception) {
            emptyList()
        }
    }
    if (pts.size < 2) {
        Text("Brak danych", fontSize = 12.sp, color = DartSubtle, modifier = modifier)
        return
    }
    Canvas(modifier = modifier) {
        try {
            val w = size.width
            val h = size.height
            val maxV = max(pts.maxOfOrNull { it.first } ?: 1, 1).toFloat()
            val n = pts.size - 1
            fun fx(i: Int) = if (n == 0) 0f else w * i / n
            fun fy(v: Int) = h * (1f - v / maxV) * 0.95f
            val path = Path()
            pts.forEachIndexed { i, (v, _) ->
                if (i == 0) path.moveTo(fx(i), fy(v)) else path.lineTo(fx(i), fy(v))
            }
            drawPath(path, color = Color(0xFF4ADE80), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
        } catch (_: Exception) {}
    }
}

@Composable
fun StatAfterScreen(data: FinishData, engine: Any, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        AppTopBar("Statystyki gry", onBack = onDone)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            data.players.indices.forEach { i ->
                val p = data.players[i]
                val perf = data.perfs.getOrNull(i) ?: Perf()
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = DartCardBg
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        when (data.type) {
                            GameType.X01 -> {
                                StatRow("Średnia 3-lotkowa", "%.1f".format(if (perf.throws > 0) perf.points.toDouble() / perf.throws * 3 else 0.0))
                                StatRow("First 9", "%.1f".format(if (perf.throws > 0) perf.first9 / 3.0 else 0.0))
                                StatRow("Najlepsza tura", perf.bestTurn.toString())
                                StatRow("Rzuty", perf.throws.toString())
                                StatRow("Podwójne", perf.doubles.toString())
                                StatRow("Potrójne", perf.triples.toString())
                                StatRow("Najwyższy checkout", if (perf.maxCheckout > 0) perf.maxCheckout.toString() else "—")
                                StatRow("Min lotek na lega", if (perf.minDartsLeg > 0) perf.minDartsLeg.toString() else "—")
                                StatRow("Legi wygrane", perf.legsWon.toString())
                                if (engine is X01Engine) {
                                    Text("Wynik w grze", fontSize = 13.sp, color = DartSubtle, modifier = Modifier.padding(top = 6.dp))
                                    X01Chart(engine, i, Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp))
                                }
                            }
                            GameType.CRICKET -> {
                                StatRow("MPR", "%.2f".format(if (perf.throws > 0) perf.marks.toDouble() / perf.throws * 3 else 0.0))
                                StatRow("Marks", perf.marks.toString())
                                StatRow("Rzuty", perf.throws.toString())
                                StatRow("Podwójne", perf.doubles.toString())
                                StatRow("Potrójne", perf.triples.toString())
                                StatRow("Legi wygrane", perf.legsWon.toString())
                            }
                            GameType.AROUND -> {
                                StatRow("Sektor", perf.points.toString())
                                StatRow("Rzuty", perf.throws.toString())
                            }
                        }
                        if (perf.hitsBySector.isNotEmpty()) {
                            Text("Trafienia w sektor", fontSize = 13.sp, color = DartSubtle, modifier = Modifier.padding(top = 6.dp))
                            Text(
                                perf.hitsBySector.entries
                                    .sortedByDescending { it.value }
                                    .joinToString("  ") { "${it.key}:${it.value}" },
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
            ) {
                Text("Wróć do podsumowania", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
