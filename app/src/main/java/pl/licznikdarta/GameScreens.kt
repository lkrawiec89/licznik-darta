package pl.licznikdarta

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun BotLevel?.orNone(): BotLevel = this ?: BotLevel.NONE

@Composable
private fun BotTurnLoop(
    engine: Any,
    state: AppState,
    audio: GameAudio,
    tts: GameTts,
    isBotActive: (Int) -> Boolean
) {
    val current: Int
    val turnKey: Int
    val pendingKey: Int
    val finishedKey: Boolean
    when (engine) {
        is X01Engine -> { current = engine.current; turnKey = engine.turnCounter; pendingKey = engine.pendingFinishIdx; finishedKey = engine.finished }
        is CricketEngine -> { current = engine.current; turnKey = engine.turnCounter; pendingKey = engine.pendingFinishIdx; finishedKey = engine.finished }
        is AroundEngine -> { current = engine.current; turnKey = engine.dartsUsed; pendingKey = -1; finishedKey = engine.finished }
        else -> return
    }
    LaunchedEffect(current, turnKey, pendingKey, finishedKey) {
        if (finishedKey || pendingKey >= 0 || !isBotActive(current)) return@LaunchedEffect
        when (engine) {
            is X01Engine -> {
                val level = engine.bots[engine.players[engine.current].id].orNone()
                val throws = Bot.x01Turn(
                    engine.scores[engine.current],
                    engine.checkedIn[engine.current],
                    3 - engine.dartsUsed,
                    engine.checkIn,
                    engine.checkOut,
                    level
                )
                for (t in throws) {
                    if (engine.finished || engine.pendingFinishIdx >= 0 || !engine.isBot(engine.current)) break
                    delay(1200)
                    val wasFinished = engine.finished
                    val hadWinner = engine.finishedOrder.isNotEmpty()
                    engine.submitDart(t)
                    val isWon = !wasFinished && engine.finishedOrder.isNotEmpty() && !hadWinner
                    if (state.settings.sounds) {
                        if (engine.lastThrowBust) {
                            audio.playBrym()
                        } else if (isWon) {
                            val winnerIdx = engine.finishedOrder.first()
                            if (engine.isBot(winnerIdx) && engine.players.indices.any { !engine.isBot(it) }) {
                                audio.playBrym()
                            } else {
                                audio.playVictory()
                            }
                        } else if (engine.lastTurnThreeZeros) {
                            audio.playLaugh()
                        } else {
                            audio.throwSound(t.mult)
                        }
                    }
                    val turnScore = engine.spoken.toIntOrNull() ?: -1
                    val playedVoice = if (!isWon && (state.settings.sounds || state.settings.voice)) {
                        if (engine.lastTurnThreeZeros) true else audio.playAnnounceIfSpecial(turnScore)
                    } else false

                    if (!playedVoice && !isWon && state.settings.voice && engine.spoken.isNotEmpty()) {
                        tts.speak(engine.spoken)
                    }
                }
            }
            is CricketEngine -> {
                val level = engine.bots[engine.players[engine.current].id].orNone()
                val oppMarks = engine.marks.filterIndexed { i, _ -> i != engine.current }
                val plan = Bot.cricketTurn(engine.marks[engine.current], oppMarks, level)
                for ((sector, mult) in plan) {
                    if (engine.finished || engine.pendingFinishIdx >= 0 || !engine.isBot(engine.current)) break
                    delay(1200)
                    val wasFinished = engine.finished
                    val hadWinner = engine.finishedOrder.isNotEmpty()
                    engine.submitDart(sector, mult)
                    if (state.settings.sounds) {
                        if (!wasFinished && engine.finishedOrder.isNotEmpty() && !hadWinner) {
                            val winnerIdx = engine.finishedOrder.first()
                            if (engine.isBot(winnerIdx) && engine.players.indices.any { !engine.isBot(it) }) {
                                audio.playBrym()
                            } else {
                                audio.playVictory()
                            }
                        } else if (engine.lastTurnThreeZeros) {
                            audio.playLaugh()
                        } else {
                            audio.throwSound(mult)
                        }
                    }
                    if (!engine.lastTurnThreeZeros && state.settings.voice && engine.spoken.isNotEmpty()) {
                        tts.speak(engine.spoken)
                    }
                }
            }
            is AroundEngine -> {
                val level = engine.bots[engine.players[engine.current].id].orNone()
                repeat(3) {
                    if (engine.finished || !engine.isBot(engine.current)) return@LaunchedEffect
                    delay(1200)
                    val (hit, mult) = Bot.aroundDart(engine.doublesOnly, level)
                    val value = if (hit == 1) {
                        if (engine.targets[engine.current] == 21) 25 else minOf(engine.targets[engine.current], 20)
                    } else 0
                    val wasFinished = engine.finished
                    engine.submit(value, mult)
                    if (state.settings.sounds) {
                        if (!wasFinished && engine.finished) {
                            if (engine.isBot(engine.winnerIdx) && engine.players.indices.any { !engine.isBot(it) }) {
                                audio.playBrym()
                            } else {
                                audio.playVictory()
                            }
                        } else if (engine.lastTurnThreeZeros) {
                            audio.playLaugh()
                        } else {
                            audio.throwSound(mult)
                        }
                    }
                    if (!engine.lastTurnThreeZeros && state.settings.voice && engine.spoken.isNotEmpty()) tts.speak(engine.spoken)
                }
            }
        }
    }
}

@Composable
private fun FirstFinishedDialog(name: String, onContinue: () -> Unit, onFinish: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("$name zakończył grę!") },
        text = { Text("Chcesz grać dalej o pozostałe miejsca, czy zakończyć grę?") },
        confirmButton = { Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = DartGreen)) { Text("Graj dalej", color = Color.Black) } },
        dismissButton = { TextButton(onClick = onFinish) { Text("Zakończ grę", color = DartRed) } }
    )
}

@Composable
private fun BackDialog(
    onSave: () -> Unit,
    onAbort: () -> Unit,
    onDismiss: () -> Unit,
    onFinishGame: (() -> Unit)? = null,
    hasFinishedPlayers: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zakończyć grę?") },
        text = {
            Text(
                when {
                    hasFinishedPlayers -> "Część graczy ukończyła już grę. Możesz zakończyć grę teraz i zapisać wyniki na podium, zapisać stan do wznowienia lub wyjść bez zapisu."
                    onFinishGame != null -> "Możesz zakończyć grę teraz i zapisać wynik na podium, zapisać stan do późniejszego wznowienia lub wyjść bez zapisu."
                    else -> "Możesz zapisać stan gry i wznowić ją w dowolnym momencie z ekranu głównego."
                }
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onFinishGame != null) {
                    Button(
                        onClick = onFinishGame,
                        colors = ButtonDefaults.buttonColors(containerColor = DartOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (hasFinishedPlayers) "Zakończ grę i zapisz wynik" else "Zakończ grę teraz",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zapisz stan i wyjdź", color = Color.Black)
                }
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onAbort,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wyjdź bez zapisu", color = DartRed)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zostań w grze", color = DartSubtle)
                }
            }
        }
    )
}

@Composable
private fun HintChips(hint: String?) {
    if (hint == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Checkout: ", fontSize = 13.sp, color = DartGreen, fontWeight = FontWeight.Bold)
        hint.split(" → ").forEach { h ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = DartGreen,
                modifier = Modifier.padding(horizontal = 3.dp)
            ) {
                Text(
                    text = h,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Ekran gry X01 (Screenshot 1)
 */
@Composable
fun X01Screen(
    engine: X01Engine,
    state: AppState,
    audio: GameAudio,
    tts: GameTts,
    onExit: () -> Unit,
    onFinish: (Any) -> Unit
) {
    var doubleOn by remember { mutableStateOf(false) }
    var tripleOn by remember { mutableStateOf(false) }
    var showBack by remember { mutableStateOf(false) }
    BackHandler { showBack = !showBack }
    var soundEnabled by remember { mutableStateOf(state.settings.sounds) }

    BotTurnLoop(engine, state, audio, tts) { i -> engine.isBot(i) }

    LaunchedEffect(engine.finished) {
        if (engine.finished) onFinish(engine)
    }

    fun dartAt(seg: Int) {
        if (engine.finished || engine.pendingFinishIdx >= 0 || engine.isBot(engine.current)) return
        val mult = when {
            seg == 0 -> 0
            seg == 25 && tripleOn -> 1
            doubleOn -> 2
            tripleOn -> 3
            else -> 1
        }
        val t = DartThrow(seg * mult, mult, seg)
        val wasFinished = engine.finished
        val hadWinner = engine.finishedOrder.isNotEmpty()
        engine.submitDart(t)
        val isWon = !wasFinished && engine.finishedOrder.isNotEmpty() && !hadWinner
        if (soundEnabled) {
            if (engine.lastThrowBust) {
                audio.playBrym()
            } else if (isWon) {
                audio.playVictory()
            } else if (engine.lastTurnThreeZeros) {
                audio.playLaugh()
            } else {
                audio.click(state.settings.keySound)
            }
        }
        val turnScore = engine.spoken.toIntOrNull() ?: -1
        val playedVoice = if (!isWon && (soundEnabled || state.settings.voice)) {
            if (engine.lastTurnThreeZeros) true else audio.playAnnounceIfSpecial(turnScore)
        } else false

        if (!playedVoice && !isWon && state.settings.voice && engine.spoken.isNotEmpty()) {
            tts.speak(engine.spoken)
        }
        doubleOn = false
        tripleOn = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        // TOP BAR: green ArrowBack + "X01" + Users icon + Sound icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showBack = true }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Wstecz",
                    tint = DartGreen,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "X01",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val newVoice = !state.settings.voice
                state.saveSettings(state.settings.copy(voice = newVoice))
                if (!newVoice) tts.stop()
            }) {
                Icon(
                    imageVector = if (state.settings.voice) Icons.Default.RecordVoiceOver else Icons.Default.VoiceOverOff,
                    contentDescription = "Lektor TTS",
                    tint = if (state.settings.voice) DartGreen else DartSubtle,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = {
                soundEnabled = !soundEnabled
                state.saveSettings(state.settings.copy(sounds = soundEnabled))
            }) {
                Icon(
                    imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Dźwięk",
                    tint = if (soundEnabled) DartGreen else DartSubtle,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Subheader: "301, Double Out, First to 1 Set 1 Leg"
        Text(
            text = "${engine.startScore}, ${engine.checkOut.pl}, ${engine.setLegMode.pl} ${engine.setsTarget} Set ${engine.legsTarget} Leg",
            fontSize = 13.sp,
            color = DartSubtle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )



        // PLAYER CARDS LIST
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val finishedIndices = engine.finishedOrder.toList()
            val activeIndices = engine.players.indices.filter { it !in finishedIndices }

            // 1. Zakończeni gracze - wyraziste karty z numerem zajętego miejsca i nazwiskiem (dokładnie jak w zrzucie ekranu)
            items(finishedIndices.size) { idx ->
                val i = finishedIndices[idx]
                val p = engine.players[i]
                val place = engine.finishedOrder.indexOf(i) + 1
                val placeColor = when (place) {
                    1 -> DartGreen
                    2 -> DartOrange
                    3 -> Color(0xFF42A5F5)
                    else -> Color.White
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = DartCardBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = place.toString(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = placeColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = p.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = DartSubtle
                        )
                    }
                }
            }

            // 2. Aktywni gracze kontynuujący rozgrywkę
            items(activeIndices.size) { idx ->
                val i = activeIndices[idx]
                val p = engine.players[i]
                val active = engine.current == i
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = DartCardBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left green indicator bar for active player
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight()
                                    .background(DartGreen)
                            )
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }

                        // Player details
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Score + Name
                            Column(Modifier.weight(1.1f)) {
                                Text(
                                    text = engine.scores[i].toString(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = p.name,
                                    fontSize = 14.sp,
                                    color = DartSubtle
                                )
                            }

                            // Center: 3 black throw boxes + current turn score
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val isBusted = engine.playerLastTurnBusted.getOrNull(i) == true
                                val displayThrows: List<DartThrow> = if (active && !isBusted) {
                                    engine.turnThrows
                                } else {
                                    engine.playerLastTurnThrows.getOrNull(i) ?: emptyList()
                                }
                                val boxBg = if (isBusted) DartRed else Color(0xFF141414)
                                val boxBorder = if (isBusted) DartRed else Color(0xFF2E2E32)

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(3) { dartIndex ->
                                        val throwItem = displayThrows.getOrNull(dartIndex)
                                        val label = throwItem?.label() ?: ""
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(boxBg, RoundedCornerShape(4.dp))
                                                .border(1.dp, boxBorder, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                val sumValue = if (isBusted) 0 else displayThrows.sumOf { it.value }
                                Text(
                                    text = sumValue.toString(),
                                    fontSize = 13.sp,
                                    color = if (isBusted) DartRed else DartSubtle
                                )
                            }

                            // Right: Sets/Legs + Dart Count + Average
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Text(
                                    text = "Sety: ${engine.setsWon[i]} Legi: ${engine.legsWonInSet[i]}",
                                    fontSize = 12.sp,
                                    color = DartSubtle
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                ) {
                                    DartIcon(color = DartSubtle, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${engine.dartsInLeg(i)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Ø ${"%.2f".format(engine.avg(i))}",
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM KEYPAD (7 COLUMNS)
        if (engine.finished) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "KONIEC GRY",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DartGreen
                )
            }
        } else if (engine.isBot(engine.current)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${engine.players[engine.current].name} rzuca…",
                    fontSize = 16.sp,
                    color = DartSubtle
                )
            }
        } else {
            val curScore = engine.scores.getOrNull(engine.current) ?: 0
            val checkoutSuggestion = if (curScore in 2..170) {
                engine.currentCheckoutHint()?.split(" → ") ?: emptyList()
            } else {
                emptyList()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Checkout suggestion boxes row (e.g. [ 20 ] [ D20 ])
                if (checkoutSuggestion.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        checkoutSuggestion.forEach { part ->
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .width(84.dp)
                                    .height(36.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF262626)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = part,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                // Row 1: 1, 2, 3, 4, 5, 6, 7
                Row(Modifier.fillMaxWidth()) {
                    for (seg in 1..7) {
                        KeyButton(
                            text = seg.toString(),
                            onClick = { dartAt(seg) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Row 2: 8, 9, 10, 11, 12, 13, 14
                Row(Modifier.fillMaxWidth()) {
                    for (seg in 8..14) {
                        KeyButton(
                            text = seg.toString(),
                            onClick = { dartAt(seg) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Row 3: 15, 16, 17, 18, 19, 20, 25
                Row(Modifier.fillMaxWidth()) {
                    for (seg in 15..20) {
                        KeyButton(
                            text = seg.toString(),
                            onClick = { dartAt(seg) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    KeyButton(
                        text = "25",
                        onClick = { dartAt(25) },
                        modifier = Modifier.weight(1f),
                        enabled = !tripleOn
                    )
                }
                // Row 4: 0 (weight 1), DOUBLE (weight 2), TRIPLE (weight 2), ← (weight 2)
                Row(Modifier.fillMaxWidth()) {
                    KeyButton(
                        text = "0",
                        onClick = { dartAt(0) },
                        modifier = Modifier.weight(1f),
                        color = DartKeypadBg
                    )
                    KeyButton(
                        text = "DOUBLE",
                        onClick = {
                            if (soundEnabled) audio.switchSound()
                            doubleOn = !doubleOn
                            tripleOn = false
                        },
                        modifier = Modifier.weight(2f),
                        color = DartOrange,
                        active = doubleOn
                    )
                    KeyButton(
                        text = "TRIPLE",
                        onClick = {
                            if (soundEnabled) audio.switchSound()
                            tripleOn = !tripleOn
                            doubleOn = false
                        },
                        modifier = Modifier.weight(2f),
                        color = DartOrange,
                        active = tripleOn
                    )
                    KeyButton(
                        text = "←",
                        onClick = {
                            if (soundEnabled) audio.click(state.settings.keySound)
                            engine.undo()
                        },
                        modifier = Modifier.weight(2f),
                        color = DartRed
                    )
                }
            }
        }
    }

    if (engine.pendingFinishIdx >= 0) {
        FirstFinishedDialog(
            name = engine.players[engine.pendingFinishIdx].name,
            onContinue = { engine.continueAfterFinish() },
            onFinish = { engine.finishNow() }
        )
    }

    DisposableEffect(engine) {
        onDispose {
            if (!engine.finished) {
                state.saveActive(engine)
            }
        }
    }

    if (showBack) {
        val hasFinished = engine.finishedOrder.isNotEmpty()
        BackDialog(
            onSave = {
                state.saveActive(engine)
                showBack = false
                onExit()
            },
            onAbort = {
                val curId = state.currentActiveGameId
                if (curId != null) {
                    state.removeActive(curId)
                    state.currentActiveGameId = null
                }
                showBack = false
                onExit()
            },
            onDismiss = { showBack = false },
            onFinishGame = {
                showBack = false
                val curId = state.currentActiveGameId
                if (curId != null) {
                    state.removeActive(curId)
                    state.currentActiveGameId = null
                }
                engine.finishNow()
                onFinish(engine)
            },
            hasFinishedPlayers = hasFinished
        )
    }
}

@Composable
fun CricketMark(
    markCount: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (markCount <= 0) return

    Canvas(modifier = modifier) {
        val strokeWidth = (size.minDimension * 0.10f).coerceIn(2.dp.toPx(), 3.5.dp.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)

        when {
            markCount == 1 -> {
                val pad = size.minDimension * 0.16f
                drawLine(
                    color = color,
                    start = Offset(pad, size.height - pad),
                    end = Offset(size.width - pad, pad),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            markCount == 2 -> {
                val pad = size.minDimension * 0.16f
                drawLine(
                    color = color,
                    start = Offset(pad, size.height - pad),
                    end = Offset(size.width - pad, pad),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(pad, pad),
                    end = Offset(size.width - pad, size.height - pad),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            else -> {
                val r = (size.minDimension - strokeWidth) / 2f
                drawCircle(
                    color = color,
                    radius = r,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
                val d = r * 0.7071f
                drawLine(
                    color = color,
                    start = Offset(center.x - d, center.y + d),
                    end = Offset(center.x + d, center.y - d),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(center.x - d, center.y - d),
                    end = Offset(center.x + d, center.y + d),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Ekran gry Cricket (Screenshot 6)
 */
@Composable
fun CricketScreen(
    engine: CricketEngine,
    state: AppState,
    audio: GameAudio,
    tts: GameTts,
    onExit: () -> Unit,
    onFinish: (Any) -> Unit
) {
    var doubleOn by remember { mutableStateOf(false) }
    var tripleOn by remember { mutableStateOf(false) }
    var showBack by remember { mutableStateOf(false) }
    BackHandler { showBack = !showBack }
    var soundEnabled by remember { mutableStateOf(state.settings.sounds) }

    BotTurnLoop(engine, state, audio, tts) { i -> engine.isBot(i) }

    LaunchedEffect(engine.finished) {
        if (engine.finished) onFinish(engine)
    }

    fun dartAt(sectorIdx: Int) {
        if (engine.finished || engine.pendingFinishIdx >= 0 || engine.isBot(engine.current)) return
        if (sectorIdx == 6 && tripleOn) return
        val mult = if (doubleOn) 2 else if (tripleOn) 3 else 1
        val wasFinished = engine.finished
        val hadWinner = engine.finishedOrder.isNotEmpty()
        engine.submitDart(sectorIdx, mult)
        if (soundEnabled) {
            if (!wasFinished && engine.finishedOrder.isNotEmpty() && !hadWinner) {
                audio.playVictory()
            } else {
                audio.throwSound(mult)
            }
        }
        if (state.settings.voice && engine.spoken.isNotEmpty()) {
            tts.speak(engine.spoken)
        }
        doubleOn = false
        tripleOn = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        // TOP BAR: ArrowBack + "Cricket" + Sound icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showBack = true }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Wstecz",
                    tint = DartGreen,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "Cricket",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val newVoice = !state.settings.voice
                state.saveSettings(state.settings.copy(voice = newVoice))
                if (!newVoice) tts.stop()
            }) {
                Icon(
                    imageVector = if (state.settings.voice) Icons.Default.RecordVoiceOver else Icons.Default.VoiceOverOff,
                    contentDescription = "Lektor TTS",
                    tint = if (state.settings.voice) DartGreen else DartSubtle,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = {
                soundEnabled = !soundEnabled
                state.saveSettings(state.settings.copy(sounds = soundEnabled))
            }) {
                Icon(
                    imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Dźwięk",
                    tint = if (soundEnabled) DartGreen else DartSubtle,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Subheader: "Points On, Cut Throat, First to 1 Set 1 Leg"
        Text(
            text = "${if (engine.pointsOn) "Points On" else "Points Off"}, ${if (engine.cutThroat) "Cut Throat" else "Normal"}, ${engine.setLegMode.pl} ${engine.setsTarget} Set ${engine.legsTarget} Leg",
            fontSize = 13.sp,
            color = DartSubtle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // CRICKET BOARD TABLE - PINNED TARGETS + HORIZONTAL SCROLL FOR PLAYERS
        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            val targetColWidth = 64.dp
            val availableWidth = maxWidth - targetColWidth
            val colWidth = if (engine.players.size in 1..2) {
                availableWidth / engine.players.size
            } else {
                maxOf(105.dp, availableWidth / 3f)
            }

            // Dynamic height calculation so board fills 100% of available vertical screen height!
            val totalH = maxHeight
            val headerH = (totalH * 0.12f).coerceIn(48.dp, 60.dp)
            val footerH = (totalH * 0.24f).coerceIn(110.dp, 135.dp)
            val targetRowH = (totalH - headerH - footerH) / 7f

            LaunchedEffect(engine.current) {
                if (engine.players.isNotEmpty()) {
                    val targetPx = with(density) { (colWidth * engine.current).toPx() }.toInt()
                    scrollState.animateScrollTo(targetPx)
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Pinned left column: target sectors (20, 19, 18, 17, 16, 15, Bull)
                Column(modifier = Modifier.width(targetColWidth)) {
                    // Top-left corner dark box (matching player header height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerH)
                            .padding(bottom = 2.dp)
                            .background(Color(0xFF242424), RoundedCornerShape(topStart = 4.dp))
                            .border(0.5.dp, Color(0xFF333333), RoundedCornerShape(topStart = 4.dp))
                    )

                    // 7 target buttons
                    CricketEngine.SECTORS.forEachIndexed { sectorIdx, sector ->
                        val isBull = sector == 25
                        val allClosed = engine.players.indices.all { p -> engine.marks[p][sectorIdx] >= 3 }
                        val cellColor = if (allClosed) Color(0xFF2C2C2E) else if (isBull) DartRed else DartGreen
                        val textColor = if (allClosed) Color(0xFF8E8E93) else Color.White
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(targetRowH)
                                .padding(vertical = 1.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = cellColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { dartAt(sectorIdx) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBull) "Bull" else sector.toString(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    textDecoration = if (allClosed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                            }
                        }
                    }

                    // Bottom-left dark box (matching player footer height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(footerH)
                            .padding(top = 2.dp)
                            .background(Color(0xFF242424), RoundedCornerShape(bottomStart = 4.dp))
                            .border(0.5.dp, Color(0xFF333333), RoundedCornerShape(bottomStart = 4.dp))
                    )
                }

                // Horizontally scrollable player columns
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                ) {
                    engine.players.forEachIndexed { playerIdx, player ->
                        val active = engine.current == playerIdx
                        val isFinished = engine.isFinishedPlayer(playerIdx)
                        Column(
                            modifier = Modifier.width(colWidth),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isFinished) {
                                // Header: dark box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(headerH)
                                        .background(Color(0xFF242424), RoundedCornerShape(topEnd = if (playerIdx == engine.players.size - 1) 4.dp else 0.dp))
                                        .border(0.5.dp, Color(0xFF333333))
                                )

                                // Middle area: Single card displaying place, name and points
                                val placeIdx = engine.finishedOrder.indexOf(playerIdx)
                                val place = if (placeIdx >= 0) placeIdx + 1 else 1
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(targetRowH * 7)
                                        .background(Color(0xFF181818))
                                        .border(0.5.dp, Color(0xFF2C2C2E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$place",
                                            fontSize = 44.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DartGreen
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = player.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        if (engine.pointsOn) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "${engine.points[playerIdx]} pkt",
                                                fontSize = 13.sp,
                                                color = DartSubtle
                                            )
                                        }
                                    }
                                }

                                // Footer: dark box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(footerH)
                                        .background(Color(0xFF242424))
                                        .border(0.5.dp, Color(0xFF333333))
                                )
                            } else {
                                // Header: Player name + Score + Green underline indicator
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(headerH)
                                        .padding(horizontal = 1.dp, vertical = 1.dp)
                                        .background(Color(0xFF242424), RoundedCornerShape(topEnd = if (playerIdx == engine.players.size - 1) 4.dp else 0.dp))
                                        .border(0.5.dp, Color(0xFF333333)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = player.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = engine.points[playerIdx].toString(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (active) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(DartGreen)
                                        )
                                    } else {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }

                                // 7 Target cells (marks)
                                CricketEngine.SECTORS.forEachIndexed { sectorIdx, _ ->
                                    val allClosed = engine.players.indices.all { p -> engine.marks[p][sectorIdx] >= 3 }
                                    val markCount = engine.marks[playerIdx][sectorIdx]
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(targetRowH)
                                            .padding(horizontal = 1.dp, vertical = 1.dp)
                                            .background(Color(0xFF181818))
                                            .border(0.5.dp, Color(0xFF2C2C2E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CricketMark(
                                            markCount = markCount,
                                            color = if (allClosed) Color(0xFF555555) else Color.White,
                                            modifier = Modifier
                                                .fillMaxHeight(0.60f)
                                                .aspectRatio(1f)
                                        )
                                    }
                                }

                                // Footer: Sety/Legi, Dart count, MPR, 3 throw boxes (persisting previous turn)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(footerH)
                                        .padding(horizontal = 1.dp, vertical = 2.dp)
                                        .background(Color(0xFF242424))
                                        .border(0.5.dp, Color(0xFF333333)),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Text(
                                        text = "Sety: ${engine.setsWon[playerIdx]}  Legi: ${engine.legsWonInSet[playerIdx]}",
                                        fontSize = 11.sp,
                                        color = DartSubtle
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        DartIcon(color = DartSubtle, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${engine.dartsCount(playerIdx)}",
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "MPR: ${"%.2f".format(engine.mpr(playerIdx))}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    // 3 square boxes for darts thrown in turn
                                    val playerThrows = if (active) {
                                        if (engine.turnThrows.isNotEmpty()) engine.turnThrows.toList() else emptyList()
                                    } else {
                                        engine.playerLastTurnThrows.getOrNull(playerIdx) ?: emptyList()
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                            .padding(bottom = 3.dp)
                                    ) {
                                        repeat(3) { dartIndex ->
                                            val th = playerThrows.getOrNull(dartIndex)
                                            val lbl = if (th != null) {
                                                val s = CricketEngine.SECTORS[th.first]
                                                when (th.second) {
                                                    0 -> "0"
                                                    2 -> if (s == 25) "D25" else "D$s"
                                                    3 -> "T$s"
                                                    else -> s.toString()
                                                }
                                            } else ""
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(28.dp)
                                                    .background(Color(0xFF3A3A3C), RoundedCornerShape(4.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = lbl,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // KEYPAD (CRICKET)
        if (engine.finished) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "KONIEC GRY",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DartGreen
                )
            }
        } else if (engine.isBot(engine.current)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${engine.players[engine.current].name} rzuca…",
                    fontSize = 16.sp,
                    color = DartSubtle
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Row 1: 15, 16, 17, 18, 19, 20, 25
                Row(Modifier.fillMaxWidth()) {
                    listOf(15, 16, 17, 18, 19, 20, 25).forEach { sec ->
                        val secIdx = CricketEngine.SECTORS.indexOf(sec)
                        val secAllClosed = if (secIdx >= 0) engine.players.indices.all { p -> engine.marks[p][secIdx] >= 3 } else false
                        KeyButton(
                            text = sec.toString(),
                            onClick = { dartAt(secIdx) },
                            modifier = Modifier.weight(1f),
                            enabled = if (sec == 25) !tripleOn else true,
                            textDecoration = if (secAllClosed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            color = if (secAllClosed) Color(0xFF282828) else DartKeypadBg
                        )
                    }
                }
                // Row 2: 0 (weight 1), DOUBLE (weight 2), TRIPLE (weight 2), ← (weight 2)
                Row(Modifier.fillMaxWidth()) {
                    KeyButton(
                        text = "0",
                        onClick = {
                            engine.submitDart(0, 0)
                            if (soundEnabled) {
                                if (engine.lastTurnThreeZeros) {
                                    audio.playLaugh()
                                } else {
                                    audio.throwSound(0)
                                }
                            }
                            if (state.settings.voice && engine.spoken.isNotEmpty() && !engine.lastTurnThreeZeros) {
                                tts.speak(engine.spoken)
                            }
                            doubleOn = false
                            tripleOn = false
                        },
                        modifier = Modifier.weight(1f),
                        color = DartKeypadBg
                    )
                    KeyButton(
                        text = "DOUBLE",
                        onClick = {
                            if (soundEnabled) audio.switchSound()
                            doubleOn = !doubleOn
                            tripleOn = false
                        },
                        modifier = Modifier.weight(2f),
                        color = DartOrange,
                        active = doubleOn
                    )
                    KeyButton(
                        text = "TRIPLE",
                        onClick = {
                            if (soundEnabled) audio.switchSound()
                            tripleOn = !tripleOn
                            doubleOn = false
                        },
                        modifier = Modifier.weight(2f),
                        color = DartOrange,
                        active = tripleOn
                    )
                    KeyButton(
                        text = "←",
                        onClick = {
                            if (soundEnabled) audio.click(state.settings.keySound)
                            engine.undo()
                        },
                        modifier = Modifier.weight(2f),
                        color = DartRed
                    )
                }
            }
        }
    }

    if (engine.pendingFinishIdx >= 0) {
        FirstFinishedDialog(
            name = engine.players[engine.pendingFinishIdx].name,
            onContinue = { engine.continueAfterFinish() },
            onFinish = { engine.finishNow() }
        )
    }

    DisposableEffect(engine) {
        onDispose {
            if (!engine.finished) {
                state.saveActive(engine)
            }
        }
    }

    if (showBack) {
        val hasFinished = engine.finishedOrder.isNotEmpty()
        BackDialog(
            onSave = {
                state.saveActive(engine)
                showBack = false
                onExit()
            },
            onAbort = {
                val curId = state.currentActiveGameId
                if (curId != null) {
                    state.removeActive(curId)
                    state.currentActiveGameId = null
                }
                showBack = false
                onExit()
            },
            onDismiss = { showBack = false },
            onFinishGame = {
                showBack = false
                val curId = state.currentActiveGameId
                if (curId != null) {
                    state.removeActive(curId)
                    state.currentActiveGameId = null
                }
                engine.finishNow()
                onFinish(engine)
            },
            hasFinishedPlayers = hasFinished
        )
    }
}

/**
 * Ekran gry Dookoła tarczy
 */
@Composable
fun AroundScreen(
    engine: AroundEngine,
    state: AppState,
    audio: GameAudio,
    tts: GameTts,
    onExit: () -> Unit,
    onFinish: (Any) -> Unit
) {
    var showBack by remember { mutableStateOf(false) }
    BackHandler { showBack = !showBack }
    var mult by remember { mutableStateOf(if (engine.doublesOnly) 2 else 1) }

    BotTurnLoop(engine, state, audio, tts) { i -> engine.isBot(i) }

    LaunchedEffect(engine.finished) {
        if (engine.finished) onFinish(engine)
    }

    fun dart(value: Int, m: Int) {
        val wasFinished = engine.finished
        engine.submit(value, m)
        if (state.settings.sounds) {
            if (!wasFinished && engine.finished) {
                audio.playVictory()
            } else if (engine.lastTurnThreeZeros) {
                audio.playLaugh()
            } else {
                audio.throwSound(m)
            }
        }
        if (!engine.lastTurnThreeZeros && state.settings.voice && engine.spoken.isNotEmpty()) tts.speak(engine.spoken)
        if (m == 2) mult = 1
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        AppTopBar("Dookoła tarczy") { showBack = true }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            items(engine.players.size) { i ->
                PlayerChip(
                    name = engine.players[i].name,
                    active = engine.current == i,
                    detail = "cel: ${if (engine.targets[i] == 21) "BULL" else engine.targets[i]}\nrzuty: ${engine.throwsPerPlayer[i]}",
                    onClick = null
                )
            }
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val t = engine.targets[engine.current]
            Text("Cel ${engine.players[engine.current].name}", fontSize = 16.sp, color = DartSubtle)
            BigNumber(if (t == 21) "BULL" else t.toString())
            TurnDots(engine.dartsUsed)
            Spacer(Modifier.height(8.dp))
            Text(engine.message, fontSize = 15.sp, color = DartSubtle)
        }

        if (engine.finished) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("KONIEC GRY", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DartGreen)
            }
        } else if (engine.isBot(engine.current)) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("${engine.players[engine.current].name} rzuca…", fontSize = 16.sp, color = DartSubtle)
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(6.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    for (seg in 1..7) KeyButton(seg.toString(), onClick = { dart(seg, mult) }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    for (seg in 8..14) KeyButton(seg.toString(), onClick = { dart(seg, mult) }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    for (seg in 15..20) KeyButton(seg.toString(), onClick = { dart(seg, mult) }, modifier = Modifier.weight(1f))
                    KeyButton("BULL", onClick = { dart(25, mult) }, modifier = Modifier.weight(1f), color = DartRed)
                }
                Row(Modifier.fillMaxWidth()) {
                    KeyButton("0", onClick = { dart(0, 0) }, modifier = Modifier.weight(1f), color = DartKeypadBg)
                    KeyButton("×1", onClick = { mult = 1 }, active = mult == 1, modifier = Modifier.weight(1.5f), color = DartKeypadBg)
                    KeyButton("×2", onClick = { mult = 2 }, active = mult == 2, modifier = Modifier.weight(1.5f), color = DartOrange)
                    KeyButton("Cofnij", onClick = { engine.undo() }, modifier = Modifier.weight(2f), color = DartRed)
                }
            }
        }
    }

    DisposableEffect(engine) {
        onDispose {
            if (!engine.finished) {
                state.saveActive(engine)
            }
        }
    }

    if (showBack) {
        BackDialog(
            onSave = {
                state.saveActive(engine)
                showBack = false
                onExit()
            },
            onAbort = {
                val curId = state.currentActiveGameId
                if (curId != null) {
                    state.removeActive(curId)
                    state.currentActiveGameId = null
                }
                showBack = false
                onExit()
            },
            onDismiss = { showBack = false },
            onFinishGame = {
                showBack = false
                val curId = state.currentActiveGameId
                if (curId != null) {
                    state.removeActive(curId)
                    state.currentActiveGameId = null
                }
                engine.finishNow()
                onFinish(engine)
            },
            hasFinishedPlayers = engine.winnerIdx >= 0
        )
    }
}
