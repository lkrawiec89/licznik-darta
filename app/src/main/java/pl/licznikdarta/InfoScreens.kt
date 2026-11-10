package pl.licznikdarta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@Composable
fun StatsTableCard(
    title: String,
    headers: List<String>,
    rows: List<List<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = DartCardBg
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C2C2E))
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.forEachIndexed { i, h ->
                        Text(
                            text = h,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DartSubtle,
                            modifier = Modifier.weight(if (i == 0) 1.3f else 1f),
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.Center
                        )
                    }
                }
                // Data rows
                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Brak danych", fontSize = 13.sp, color = DartSubtle)
                    }
                } else {
                    rows.forEachIndexed { rowIndex, rowData ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowData.forEachIndexed { colIndex, cell ->
                                Text(
                                    text = cell,
                                    fontSize = 13.sp,
                                    fontWeight = if (colIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = Color.White,
                                    modifier = Modifier.weight(if (colIndex == 0) 1.3f else 1f),
                                    textAlign = if (colIndex == 0) TextAlign.Start else TextAlign.Center
                                )
                            }
                        }
                        if (rowIndex < rows.size - 1) {
                            HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ekran "Statystyka" (Screenshot 5)
 */
@Composable
fun StatsScreen(state: AppState) {
    var gameTypeIndex by remember { mutableStateOf(0) } // 0 = X01, 1 = Cricket
    val selectedGameType = if (gameTypeIndex == 0) GameType.X01 else GameType.CRICKET

    var periodIndex by remember { mutableStateOf(3) } // 0=Dzisiaj, 1=7 dni, 2=30 dni, 3=Caly czas (domyslnie Caly czas)
    val now = System.currentTimeMillis()
    val todayCal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val since = when (periodIndex) {
        0 -> todayCal.timeInMillis
        1 -> now - 7 * 24 * 3600_000L
        2 -> now - 30L * 24 * 3600_000L
        else -> 0L
    }

    var confirmReset by remember { mutableStateOf(false) }

    val playerStatsList = remember(state.games, state.players, selectedGameType, since) {
        state.players.map { player ->
            Stats.compute(state.games, player.name, since, selectedGameType)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        // TOP BAR: "Statystyka" + Filter icon
        AppTopBar(
            title = "Statystyka",
            onBack = null,
            actions = {
                IconButton(onClick = {
                    periodIndex = (periodIndex + 1) % 4
                }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtruj",
                        tint = DartGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // SEGMENTED TABS: [ X01 ] [ Cricket ]
            item {
                Spacer(Modifier.height(4.dp))
                SegmentedTabs(
                    items = listOf("X01", "Cricket"),
                    selectedIndex = gameTypeIndex,
                    onSelect = { gameTypeIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp)
                )
                Spacer(Modifier.height(14.dp))
            }

            // SEGMENTED PERIOD TABS: [ Dzisiaj ] [ 7 dni ] [ 30 dni ] [ Cały czas ]
            item {
                SegmentedTabs(
                    items = listOf("Dzisiaj", "7 dni", "30 dni", "Cały czas"),
                    selectedIndex = periodIndex,
                    onSelect = { periodIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }

            // 1. "Gry": Gracze | Gry | Wygrane | Wygrane %
            item {
                val rows = playerStatsList.filter { it.games > 0 }.map {
                    listOf(
                        it.player,
                        it.games.toString(),
                        it.wins.toString(),
                        "${"%.0f".format(it.winRate)}%"
                    )
                }
                StatsTableCard(
                    title = "Gry",
                    headers = listOf("Gracze", "Gry", "Wygrane", "Wygrane %"),
                    rows = rows
                )
            }

            // 2. "Legi": Gracze | Legi | Legs Won | Legs Win %
            item {
                val rows = playerStatsList.filter { it.legsPlayed > 0 || it.legsWon > 0 }.map {
                    listOf(
                        it.player,
                        it.legsPlayed.toString(),
                        it.legsWon.toString(),
                        "${"%.0f".format(it.legsWinRate)}%"
                    )
                }
                StatsTableCard(
                    title = "Legi",
                    headers = listOf("Gracze", "Legi", "Legs Won", "Legs Win %"),
                    rows = rows
                )
            }

            // 3. "Rzutki": Gracze | Rzutki Ø | Double % | Triple %
            item {
                val rows = playerStatsList.filter { it.games > 0 }.map {
                    listOf(
                        it.player,
                        "%.1f".format(it.avgDartsPerLeg),
                        "${"%.0f".format(it.doublePercent)}%",
                        "${"%.0f".format(it.triplePercent)}%"
                    )
                }
                StatsTableCard(
                    title = "Rzutki",
                    headers = listOf("Gracze", "Rzutki Ø", "Double %", "Triple %"),
                    rows = rows
                )
            }

            // 4. "Średnie i Najwyższe Punkty": Gracze | Średni Ø | Pierwsi-9 Ø | Max. Punkty
            item {
                val rows = playerStatsList.filter { it.games > 0 }.map {
                    listOf(
                        it.player,
                        "%.2f".format(if (selectedGameType == GameType.CRICKET) it.mpr else it.avg),
                        "%.2f".format(it.first9),
                        it.bestTurn.toString()
                    )
                }
                StatsTableCard(
                    title = "Średnie i Najwyższe Punkty",
                    headers = listOf(
                        "Gracze",
                        if (selectedGameType == GameType.CRICKET) "MPR Ø" else "Średni Ø",
                        "Pierwsi-9 Ø",
                        "Max. Punkty"
                    ),
                    rows = rows
                )
            }

            // 5. "Punkty": Gracze | 60+ | 100+ | 140+ | 180
            item {
                val rows = playerStatsList.filter { it.games > 0 }.map {
                    listOf(
                        it.player,
                        it.p60.toString(),
                        it.p100.toString(),
                        it.p140.toString(),
                        it.p180.toString()
                    )
                }
                StatsTableCard(
                    title = "Punkty",
                    headers = listOf("Gracze", "60+", "100+", "140+", "180"),
                    rows = rows
                )
            }

            // 6. "Checkout": Gracze | Max Checkout | Min Darts | Checkout %
            item {
                val rows = playerStatsList.filter { it.games > 0 }.map {
                    listOf(
                        it.player,
                        if (it.maxCheckout > 0) it.maxCheckout.toString() else "–",
                        if (it.minDartsLeg > 0) it.minDartsLeg.toString() else "–",
                        "${"%.0f".format(it.checkoutPercent)}%"
                    )
                }
                StatsTableCard(
                    title = "Checkout",
                    headers = listOf("Gracze", "Max Checkout", "Min Darts", "Checkout %"),
                    rows = rows
                )
            }

            item {
                if (state.games.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wyzeruj wszystkie statystyki", color = DartRed, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Wyzerować statystyki?") },
            text = { Text("Wszystkie rozegrane gry zostaną trwale usunięte. Gracze pozostaną na liście.") },
            confirmButton = {
                Button(
                    onClick = {
                        state.clearGames()
                        confirmReset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartRed)
                ) {
                    Text("Resetuj", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Anuluj", color = DartSubtle) }
            }
        )
    }
}

/**
 * Ekran "Wszystkie gry" (Screenshot 3)
 */
@Composable
fun HistoryScreen(
    state: AppState,
    onResume: (SavedGame) -> Unit,
    onDeleteActive: (String) -> Unit = {},
    onFinishActive: (SavedGame) -> Unit = {}
) {
    var filterIndex by remember { mutableStateOf(0) } // 0 = Wszystkie gry, 1 = X01, 2 = Cricket
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var selectedFinishedGame by remember { mutableStateOf<GameRecord?>(null) }
    var gameToDeleteId by remember { mutableStateOf<String?>(null) }

    val activeFiltered = remember(state.activeGames, filterIndex) {
        state.activeGames.filter {
            when (filterIndex) {
                1 -> it.type == GameType.X01
                2 -> it.type == GameType.CRICKET
                else -> true
            }
        }
    }

    val finishedFiltered = remember(state.games, filterIndex) {
        state.games.filter {
            when (filterIndex) {
                1 -> it.type == GameType.X01
                2 -> it.type == GameType.CRICKET
                else -> true
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        // TOP BAR: "Wszystkie gry" + Trash icon
        AppTopBar(
            title = "Wszystkie gry",
            onBack = null,
            actions = {
                if (state.games.isNotEmpty() || state.activeGames.isNotEmpty()) {
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Wyczyść historię",
                            tint = DartGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // FILTER TABS: [ Wszystkie gry ] [ X01 ] [ Cricket ]
            item {
                Spacer(Modifier.height(4.dp))
                SegmentedTabs(
                    items = listOf("Wszystkie gry", "X01", "Cricket"),
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
            }

            if (activeFiltered.isEmpty() && finishedFiltered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak gier w historii.",
                            fontSize = 15.sp,
                            color = DartSubtle
                        )
                    }
                }
            } else {
                // 1. ACTIVE GAMES (IN PROGRESS - Exact layout from screenshot 1000079514)
                items(activeFiltered) { game ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onResume(game) },
                        shape = RoundedCornerShape(8.dp),
                        color = DartCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DartRed.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Top row: Date & "IN PROGRESS" in RED
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = df.format(Date(game.date)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "IN PROGRESS",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DartRed
                                )
                            }

                            // Subtitle: Game summary
                            Text(
                                text = game.summary.ifEmpty { game.type.pl },
                                fontSize = 12.sp,
                                color = DartSubtle,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )

                            // Players list with remaining scores & sets/legs
                            val playersToShow = if (game.progress.isNotEmpty()) {
                                game.progress
                            } else {
                                game.players.map { PlayerProgress(it.name, if (game.type == GameType.X01) 301 else 0) }
                            }

                            playersToShow.forEachIndexed { idx, p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${p.name}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Sety: ${p.setsWon} Legi: ${p.legsWon}",
                                            fontSize = 12.sp,
                                            color = DartSubtle
                                        )
                                        Text(
                                            text = p.score.toString(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                if (idx < playersToShow.size - 1) {
                                    HorizontalDivider(color = Color(0xFF333333))
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            // Action row: Usuń + Zakończ + Wznów
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { gameToDeleteId = game.id }
                                ) {
                                    Text("Usuń", color = DartRed, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onFinishActive(game) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DartOrange),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Zakończ", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onResume(game) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Wznów ▶", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. FINISHED GAMES (SKOŃCZONE - Clickable to open full stats)
                items(finishedFiltered) { game ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { selectedFinishedGame = game },
                        shape = RoundedCornerShape(8.dp),
                        color = DartCardBg
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Top row: Date & "SKOŃCZONE" in GREEN
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = df.format(Date(game.date)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "SKOŃCZONE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DartGreen
                                )
                            }

                            // Subtitle: Game details summary
                            Text(
                                text = game.summary.lines().firstOrNull() ?: game.type.pl,
                                fontSize = 12.sp,
                                color = DartSubtle,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )

                            // Players list with scores
                            val playersToShow = if (game.places.isNotEmpty()) game.places else game.players
                            playersToShow.forEachIndexed { idx, playerName ->
                                val perf = game.perPlayer[playerName]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}. $playerName",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Sety: ${if (idx == 0) 1 else 0} Legi: ${perf?.legsWon ?: 0}",
                                            fontSize = 12.sp,
                                            color = DartSubtle
                                        )
                                        Text(
                                            text = if (idx == 0) "0" else (perf?.minDartsLeg?.toString() ?: "0"),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                if (idx < playersToShow.size - 1) {
                                    HorizontalDivider(color = Color(0xFF333333))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialog with detailed stats for finished game
    selectedFinishedGame?.let { game ->
        AlertDialog(
            onDismissRequest = { selectedFinishedGame = null },
            title = {
                Text(
                    text = "Statystyki gry",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "${df.format(Date(game.date))} · ${game.summary.lines().firstOrNull() ?: game.type.pl}",
                        fontSize = 12.sp,
                        color = DartSubtle
                    )
                    if (game.winner.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Wygrywa: ${game.winner} 🏆",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DartGreen
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    val players = if (game.places.isNotEmpty()) game.places else game.players
                    players.forEach { playerName ->
                        val perf = game.perPlayer[playerName] ?: Perf()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2C2C2E)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(playerName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DartGreen)
                                Spacer(Modifier.height(4.dp))
                                when (game.type) {
                                    GameType.X01 -> {
                                        StatRow("Średnia 3-lotkowa", "%.1f".format(if (perf.throws > 0) perf.points.toDouble() / perf.throws * 3 else 0.0))
                                        StatRow("First 9", "%.1f".format(if (perf.throws > 0) perf.first9 / 3.0 else 0.0))
                                        StatRow("Najlepsza tura", perf.bestTurn.toString())
                                        StatRow("Rzuty", perf.throws.toString())
                                        StatRow("Podwójne", perf.doubles.toString())
                                        StatRow("Potrójne", perf.triples.toString())
                                        StatRow("Najwyższy checkout", if (perf.maxCheckout > 0) perf.maxCheckout.toString() else "—")
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
                                    Text("Trafienia: " + perf.hitsBySector.entries.sortedByDescending { it.value }.take(8).joinToString("  ") { "${it.key}:${it.value}" },
                                        fontSize = 11.sp, color = DartSubtle)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedFinishedGame = null },
                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                ) {
                    Text("Zamknij", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.deleteGames(setOf(game.id))
                        selectedFinishedGame = null
                    }
                ) {
                    Text("Usuń grę", color = DartRed)
                }
            }
        )
    }

    if (gameToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { gameToDeleteId = null },
            title = { Text("Usunąć trwającą grę?") },
            text = { Text("Ta nierozegrana gra zostanie bezpowrotnie usunięta.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteActive(gameToDeleteId!!)
                        gameToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartRed)
                ) {
                    Text("Usuń", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToDeleteId = null }) { Text("Anuluj", color = DartSubtle) }
            }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Wyczyścić historię?") },
            text = { Text("Wszystkie wpisy historii i trwające gry zostaną bezpowrotnie usunięte.") },
            confirmButton = {
                Button(
                    onClick = {
                        state.clearGames()
                        state.clearAllActive()
                        confirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartRed)
                ) {
                    Text("Usuń wszystko", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Anuluj", color = DartSubtle) }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    state: AppState,
    tts: GameTts,
    audio: GameAudio? = null,
    onOpenCheckouts: () -> Unit,
    onOpenCustomAudio: () -> Unit = {}
) {
    val context = LocalContext.current
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = Store.exportDatabase(context)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Baza danych wyeksportowana pomyślnie!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd eksportu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (jsonStr != null) {
                    pendingImportJson = jsonStr
                    showImportConfirmDialog = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd odczytu pliku: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    var sounds by remember { mutableStateOf(state.settings.sounds) }
    var voice by remember { mutableStateOf(state.settings.voice) }
    var botLevel by remember { mutableStateOf(state.settings.botLevel) }
    var rate by remember { mutableStateOf(state.settings.speechRate) }
    var pitch by remember { mutableStateOf(state.settings.pitch) }
    var fontSize by remember { mutableStateOf(state.settings.fontScale) }
    var keySound by remember { mutableStateOf(state.settings.keySound) }
    var autoCheckUpdates by remember { mutableStateOf(state.settings.autoCheckUpdates) }
    var updateUrl by remember { mutableStateOf(state.settings.updateUrl) }
    var showUpdateUrlConfig by remember { mutableStateOf(false) }
    var checkStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        AppTopBar(title = "Ustawienia", onBack = null)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = DartCardBg) {
                Column(Modifier.padding(16.dp)) {
                    // Dźwięki
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dźwięki", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Sygnały audio przy rzutach", fontSize = 12.sp, color = DartSubtle)
                        }
                        Switch(
                            checked = sounds,
                            onCheckedChange = {
                                sounds = it
                                state.saveSettings(state.settings.copy(sounds = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DartGreen)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(14.dp))

                    // Dźwięk klawiszy
                    Text("Dźwięk klawiszy", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Wybierz styl dźwięku przy wprowadzaniu rzutów", fontSize = 12.sp, color = DartSubtle)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KeySound.entries.forEach { ks ->
                            val isSelected = keySound == ks
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clickable {
                                        keySound = ks
                                        state.saveSettings(state.settings.copy(keySound = ks))
                                        audio?.click(ks)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DartGreen else Color(0xFF2C2C2E)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            keySound = ks
                                            state.saveSettings(state.settings.copy(keySound = ks))
                                            audio?.click(ks)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color.Black,
                                            unselectedColor = Color.Gray
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        ks.pl,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { audio?.click(ks) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VolumeUp,
                                            contentDescription = "Odsłuchaj",
                                            tint = if (isSelected) Color.Black else DartGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(14.dp))

                    // Głos (TTS)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Głos (TTS)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Komunikaty głosowe po turach", fontSize = 12.sp, color = DartSubtle)
                        }
                        Switch(
                            checked = voice,
                            onCheckedChange = {
                                voice = it
                                state.saveSettings(state.settings.copy(voice = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DartGreen)
                        )
                    }

                    if (voice) {
                        Spacer(Modifier.height(12.dp))
                        Text("Szybkość mowy", fontSize = 13.sp, color = DartSubtle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = rate,
                                onValueChange = { rate = it; tts.setRate(it) },
                                onValueChangeFinished = { state.saveSettings(state.settings.copy(speechRate = rate)) },
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = DartGreen, activeTrackColor = DartGreen)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${"%.1f".format(rate)}x", fontSize = 13.sp, color = Color.White)
                        }

                        Text("Wysokość głosu", fontSize = 13.sp, color = DartSubtle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = pitch,
                                onValueChange = { pitch = it; tts.setPitch(it) },
                                onValueChangeFinished = { state.saveSettings(state.settings.copy(pitch = pitch)) },
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = DartGreen, activeTrackColor = DartGreen)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${"%.1f".format(pitch)}x", fontSize = 13.sp, color = Color.White)
                        }

                        Button(
                            onClick = { tts.setRate(rate); tts.setPitch(pitch); tts.speak("Test głosu") },
                            colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                        ) {
                            Text("Przetestuj głos", color = Color.Black)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(14.dp))

                    // Domyślny poziom DartBota
                    Text("Domyślny poziom DartBota", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(BotLevel.EASY, BotLevel.NORMAL, BotLevel.HARD).forEach { l ->
                            val sel = botLevel == l
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable {
                                        botLevel = l
                                        state.saveSettings(state.settings.copy(botLevel = l))
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) DartGreen else Color(0xFF2C2C2E)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        l.pl,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sel) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(14.dp))

                    // Skalowanie czcionki
                    Text("Skalowanie czcionki", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            onValueChangeFinished = { state.saveSettings(state.settings.copy(fontScale = fontSize)) },
                            valueRange = 0.75f..1.5f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = DartGreen, activeTrackColor = DartGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${"%.0f".format(fontSize * 100)}%", fontSize = 13.sp, color = Color.White)
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(14.dp))

                    // Tabela checkoutów
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCheckouts),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tabela checkoutów (2–170)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text("›", fontSize = 20.sp, color = DartGreen)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Własne dźwięki punktów
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCustomAudio),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = DartGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Własne dźwięki punktów",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    "Przypisz własne pliki audio (mp3, wav) pod wybrane wyniki",
                                    fontSize = 11.sp,
                                    color = DartSubtle
                                )
                            }
                            Text("›", fontSize = 20.sp, color = DartGreen)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(14.dp))

                    // Baza danych: Eksport / Import / Udostępnij
                    Text("Kopia zapasowa bazy danych", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Eksportuj lub importuj bazę graczy, historię gier i ustawienia.", fontSize = 12.sp, color = DartSubtle)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val dFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                                val dStr = dFormat.format(Date())
                                exportLauncher.launch("dart_scoreboard_backup_$dStr.json")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Eksportuj", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            border = BorderStroke(1.dp, DartGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = DartGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Importuj", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val jsonStr = Store.exportDatabase(context)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, jsonStr)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Udostępnij kopię zapasową"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Błąd udostępniania: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF2C2C2E), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Udostępnij", tint = DartGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
                if (showImportConfirmDialog && pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportJson = null
            },
            title = { Text("Import bazy danych") },
            text = {
                Text("Czy na pewno chcesz zaimportować bazę danych? Spowoduje to zastąpienie bieżącej bazy graczy, gier i ustawień danymi z pliku.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingImportJson
                        if (json != null && Store.importDatabase(context, json)) {
                            state.reloadAll()
                            Toast.makeText(context, "Baza danych została pomyślnie zaimportowana!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Błąd importu: nieprawidłowy format pliku.", Toast.LENGTH_LONG).show()
                        }
                        showImportConfirmDialog = false
                        pendingImportJson = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                ) {
                    Text("Zaimportuj", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportJson = null
                }) {
                    Text("Anuluj", color = DartSubtle)
                }
            }
        )
    }

            Spacer(Modifier.height(16.dp))

            // Sekcja aktualizacji OTA
            Surface(shape = RoundedCornerShape(10.dp), color = DartCardBg) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = DartGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Aktualizacje aplikacji (OTA)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Zainstalowana wersja: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        fontSize = 13.sp,
                        color = DartSubtle
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatyczne sprawdzanie",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Sprawdzaj nową wersję przy uruchomieniu aplikacji",
                                fontSize = 11.sp,
                                color = DartSubtle
                            )
                        }
                        Switch(
                            checked = autoCheckUpdates,
                            onCheckedChange = {
                                autoCheckUpdates = it
                                state.saveSettings(state.settings.copy(autoCheckUpdates = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DartGreen
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                checkStatus = UpdateStatus.Checking
                                val targetUrl = updateUrl.ifBlank { UpdateManager.DEFAULT_UPDATE_URL }
                                scope.launch {
                                    val res = UpdateManager.checkUpdate(targetUrl, BuildConfig.VERSION_CODE)
                                    if (res.isSuccess) {
                                        val info = res.getOrThrow()
                                        if (info != null) {
                                            checkStatus = UpdateStatus.Available(info)
                                        } else {
                                            checkStatus = UpdateStatus.UpToDate
                                        }
                                    } else {
                                        checkStatus = UpdateStatus.Error(res.exceptionOrNull()?.message ?: "Błąd połączenia")
                                    }
                                }
                            },
                            enabled = checkStatus !is UpdateStatus.Checking,
                            colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (checkStatus is UpdateStatus.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Sprawdzanie...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sprawdź aktualizacje", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { showUpdateUrlConfig = !showUpdateUrlConfig },
                            border = BorderStroke(1.dp, if (showUpdateUrlConfig) DartGreen else Color(0xFF444444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Konfiguracja URL",
                                tint = if (showUpdateUrlConfig) DartGreen else DartSubtle,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showUpdateUrlConfig) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = updateUrl,
                            onValueChange = {
                                updateUrl = it
                                state.saveSettings(state.settings.copy(updateUrl = it))
                            },
                            label = { Text("Własny adres URL aktualizacji (JSON lub GitHub Releases API)", fontSize = 11.sp) },
                            placeholder = { Text(UpdateManager.DEFAULT_UPDATE_URL, fontSize = 11.sp, color = DartSubtle) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DartGreen,
                                unfocusedBorderColor = Color(0xFF444444),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        if (updateUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    updateUrl = ""
                                    state.saveSettings(state.settings.copy(updateUrl = ""))
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Przywróć domyślny URL", fontSize = 11.sp, color = DartGreen)
                            }
                        }
                    }

                    when (val currentStatus = checkStatus) {
                        is UpdateStatus.UpToDate -> {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DartGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Aplikacja jest aktualna!", fontSize = 12.sp, color = DartGreen)
                            }
                        }
                        is UpdateStatus.Error -> {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DartRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Błąd: ${currentStatus.message}", fontSize = 12.sp, color = DartRed)
                            }
                        }
                        is UpdateStatus.Available -> {
                            UpdateDialog(
                                info = currentStatus.info,
                                onDismiss = { checkStatus = UpdateStatus.Idle }
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Dart Scoreboard v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = DartSubtle)
            Text("Twój asystent i licznik punktów do gry w darta.", fontSize = 12.sp, color = DartSubtle)
        }
    }
}

@Composable
fun CheckoutScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filter = query.toIntOrNull()
    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        AppTopBar("Tabela checkoutów", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Szukaj (np. 40)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            val all = (2..170).toList()
            items(if (filter != null) listOf(filter) else all) { n ->
                val c = Checkouts.checkout(n)
                if (c != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(n.toString(), Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text(c, fontSize = 16.sp, color = DartGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomAudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var soundsMap by remember { mutableStateOf(CustomAudioManager.loadCustomSounds(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var scoreInput by remember { mutableStateOf("") }
    var pendingScoreForPicker by remember { mutableStateOf<Int?>(null) }
    var currentlyPlayingScore by remember { mutableStateOf<Int?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val score = pendingScoreForPicker
        if (uri != null && score != null) {
            val entry = CustomAudioManager.importSoundFromUri(context, uri, score)
            if (entry != null) {
                soundsMap = CustomAudioManager.loadCustomSounds(context)
                Toast.makeText(context, "Dźwięk przypisany do $score!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Błąd importu pliku audio", Toast.LENGTH_LONG).show()
            }
        }
        pendingScoreForPicker = null
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                val tempZip = File(context.cacheDir, "dart_sounds_export.zip")
                if (CustomAudioManager.exportToZip(context, tempZip)) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        tempZip.inputStream().use { it.copyTo(os) }
                    }
                    tempZip.delete()
                    Toast.makeText(context, "Paczka dźwięków wyeksportowana pomyślnie!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Brak dźwięków do wyeksportowania", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd eksportu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val tempZip = File(context.cacheDir, "dart_sounds_import.zip")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempZip).use { output ->
                        inputStream.copyTo(output)
                    }
                }
                if (CustomAudioManager.importFromZip(context, tempZip)) {
                    soundsMap = CustomAudioManager.loadCustomSounds(context)
                    Toast.makeText(context, "Paczka dźwięków zaimportowana pomyślnie!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Błąd importu: nieprawidłowa paczka ZIP", Toast.LENGTH_LONG).show()
                }
                tempZip.delete()
            } catch (e: Exception) {
                Toast.makeText(context, "Błąd importu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CustomAudioManager.stopPlaying()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        AppTopBar("Własne dźwięki punktów", onBack)

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Nagłówek i przyciski eksportu / importu ZIP
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = DartCardBg
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Kopia zapasowa paczki dźwięków",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Eksportuj lub importuj całą paczkę (konfiguracja + pliki audio) w jednym pliku .zip",
                        fontSize = 12.sp,
                        color = DartSubtle
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (soundsMap.isEmpty()) {
                                    Toast.makeText(context, "Dodaj najpierw dźwięki, aby wyeksportować paczkę", Toast.LENGTH_SHORT).show()
                                } else {
                                    exportZipLauncher.launch("dart_sounds_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.zip")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = DartGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Eksportuj", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = DartGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importuj", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        IconButton(
                            onClick = {
                                if (soundsMap.isEmpty()) {
                                    Toast.makeText(context, "Brak dźwięków do udostępnienia", Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val shareFile = File(exportsDir, "dart_sounds_pack.zip")
                                        if (CustomAudioManager.exportToZip(context, shareFile)) {
                                            val shareUri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                shareFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Udostępnij paczkę dźwięków"))
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Błąd udostępniania: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF2C2C2E), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Udostępnij", tint = DartGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Przycisk dodawania nowego dźwięku
            Button(
                onClick = {
                    scoreInput = ""
                    showAddDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Przypisz dźwięk do punktów", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Lista przypisanych dźwięków
            Text(
                "Przypisane dźwięki (${soundsMap.size}):",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (soundsMap.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = DartCardBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = DartSubtle,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Brak własnych dźwięków",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Kliknij powyższy przycisk, aby dodać dźwięk np. dla 180, 140, 100 lub 0 (pudło).",
                            fontSize = 12.sp,
                            color = DartSubtle,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    val sortedEntries = soundsMap.values.sortedByDescending { it.score }
                    items(sortedEntries, key = { it.score }) { entry ->
                        val isPlaying = currentlyPlayingScore == entry.score
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DartCardBg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            if (entry.score == 180) DartRed else if (entry.score == 0) Color(0xFF555555) else DartGreen,
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = entry.score.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (entry.score == 180 || entry.score == 0) Color.White else Color.Black
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (entry.score == 0) "0 pkt (Pudło / Bust)" else "${entry.score} pkt",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = entry.originalName,
                                        fontSize = 11.sp,
                                        color = DartSubtle,
                                        maxLines = 1
                                    )
                                }

                                // Odsłuchaj
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            CustomAudioManager.stopPlaying()
                                            currentlyPlayingScore = null
                                        } else {
                                            currentlyPlayingScore = entry.score
                                            val started = CustomAudioManager.playSound(context, entry.fileName) {
                                                currentlyPlayingScore = null
                                            }
                                            if (!started) currentlyPlayingScore = null
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                        contentDescription = "Odsłuchaj",
                                        tint = if (isPlaying) DartRed else DartGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Zmień plik
                                IconButton(
                                    onClick = {
                                        pendingScoreForPicker = entry.score
                                        filePickerLauncher.launch(arrayOf("audio/*"))
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Zmień",
                                        tint = DartSubtle,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Usuń
                                IconButton(
                                    onClick = {
                                        CustomAudioManager.removeSound(context, entry.score)
                                        soundsMap = CustomAudioManager.loadCustomSounds(context)
                                        Toast.makeText(context, "Usunięto dźwięk dla ${entry.score}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Usuń",
                                        tint = DartRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Dodaj własny dźwięk", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Wpisz liczbę punktów w kolejce (np. 180, 140, 100, 26 lub 0 dla pudła):",
                        fontSize = 13.sp,
                        color = DartSubtle
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = scoreInput,
                        onValueChange = { scoreInput = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Liczba punktów (0–180)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DartGreen,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val score = scoreInput.toIntOrNull()
                        if (score != null && score in 0..180) {
                            showAddDialog = false
                            pendingScoreForPicker = score
                            filePickerLauncher.launch(arrayOf("audio/*"))
                        } else {
                            Toast.makeText(context, "Podaj liczbę od 0 do 180", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                ) {
                    Text("Wybierz plik audio", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Anuluj", color = DartSubtle)
                }
            },
            containerColor = DartCardBg
        )
    }
}

