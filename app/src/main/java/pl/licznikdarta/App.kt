package pl.licznikdarta

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class Screen {
    data class Main(val tab: MainTab = MainTab.START) : Screen()
    data class PlayX01(val engine: X01Engine) : Screen()
    data class PlayCricket(val engine: CricketEngine) : Screen()
    data class PlayAround(val engine: AroundEngine) : Screen()
    data class Finish(val data: FinishData, val engine: Any) : Screen()
    data class StatAfter(val data: FinishData, val engine: Any) : Screen()
    object Checkout : Screen()
}

fun extractProgress(engine: Any): List<PlayerProgress> = when (engine) {
    is X01Engine -> engine.players.indices.map { i ->
        PlayerProgress(
            name = engine.players[i].name,
            score = engine.scores.getOrElse(i) { engine.startScore },
            setsWon = engine.setsWon.getOrElse(i) { 0 },
            legsWon = engine.legsWonTotal.getOrElse(i) { 0 }
        )
    }
    is CricketEngine -> engine.players.indices.map { i ->
        PlayerProgress(
            name = engine.players[i].name,
            score = engine.points.getOrElse(i) { 0 },
            setsWon = engine.setsWon.getOrElse(i) { 0 },
            legsWon = engine.legsWonTotal.getOrElse(i) { 0 }
        )
    }
    is AroundEngine -> engine.players.indices.map { i ->
        PlayerProgress(
            name = engine.players[i].name,
            score = engine.targets.getOrElse(i) { 1 },
            setsWon = 0,
            legsWon = 0
        )
    }
    else -> emptyList()
}

fun extractSummary(engine: Any): String = when (engine) {
    is X01Engine -> "X01 (${engine.startScore}, ${engine.checkOut.pl}, ${engine.setLegMode.en} ${engine.setsTarget} Set ${engine.legsTarget} Leg)"
    is CricketEngine -> "CRICKET (${if (engine.pointsOn) "On" else "Off"}${if (engine.cutThroat) ", Cut Throat" else ""}, ${engine.setLegMode.en} ${engine.setsTarget} Set ${engine.legsTarget} Leg)"
    is AroundEngine -> "Dookoła tarczy (${if (engine.doublesOnly) "Tylko podwójne" else "Standard"})"
    else -> ""
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

class AppState(val ctx: Context) {
    var players by mutableStateOf(Store.loadPlayers(ctx))
    var games by mutableStateOf(Store.loadGames(ctx))
    var settings by mutableStateOf(Store.loadSettings(ctx))
    var lastLineup by mutableStateOf(Store.loadLastLineup(ctx))
    var lastSetup by mutableStateOf(Store.loadLastSetup(ctx))
    var activeGames by mutableStateOf(Store.loadActiveGames(ctx))
    var currentActiveGameId by mutableStateOf<String?>(null)

    fun saveLastLineup(lineup: List<String>) {
        lastLineup = lineup
        Store.saveLastLineup(ctx, lineup)
    }

    fun savePlayers(p: List<Player>) {
        players = p
        Store.savePlayers(ctx, p)
        val valid = lastLineup.filter { id -> p.any { it.id == id } }
        if (valid.size != lastLineup.size) {
            saveLastLineup(valid)
        }
    }
    fun removeGamesWithPlayer(name: String) { games = games.filter { name !in it.players }; Store.saveGames(ctx, games) }
    fun addGame(g: GameRecord) { games = listOf(g) + games; Store.saveGames(ctx, games) }
    fun deleteGames(ids: Set<String>) { games = games.filter { it.id !in ids }; Store.saveGames(ctx, games) }
    fun clearGames() { games = emptyList(); Store.saveGames(ctx, games) }
    fun saveSettings(s: Settings) { settings = s; Store.saveSettings(ctx, s) }

    fun saveLastSetup(s: LastGameSetup) {
        lastSetup = s
        Store.saveLastSetup(ctx, s)
    }

    fun reloadAll() {
        players = Store.loadPlayers(ctx)
        games = Store.loadGames(ctx)
        settings = Store.loadSettings(ctx)
        lastLineup = Store.loadLastLineup(ctx)
        lastSetup = Store.loadLastSetup(ctx)
        activeGames = Store.loadActiveGames(ctx)
    }

    fun saveActive(engine: Any, gameId: String? = currentActiveGameId): String {
        val id = gameId ?: currentActiveGameId ?: UUID.randomUUID().toString()
        currentActiveGameId = id
        val (type, json, bots, pList) = when (engine) {
            is X01Engine -> Quad(GameType.X01, engine.toJson().toString(), engine.bots, engine.players)
            is CricketEngine -> Quad(GameType.CRICKET, engine.toJson().toString(), engine.bots, engine.players)
            is AroundEngine -> Quad(GameType.AROUND, engine.toJson().toString(), engine.bots, engine.players)
            else -> return id
        }
        val summary = extractSummary(engine)
        val progress = extractProgress(engine)
        val existing = activeGames.find { it.id == id }
        val date = existing?.date ?: System.currentTimeMillis()
        val saved = SavedGame(
            id = id,
            date = date,
            type = type,
            summary = summary,
            engineJson = json,
            bots = bots,
            players = pList,
            progress = progress,
            inProgress = true
        )
        activeGames = listOf(saved) + activeGames.filterNot { it.id == id }
        Store.saveActiveGames(ctx, activeGames)
        return id
    }

    fun removeActive(id: String) {
        if (id.isEmpty()) return
        activeGames = activeGames.filterNot { it.id == id }
        Store.saveActiveGames(ctx, activeGames)
        if (currentActiveGameId == id) currentActiveGameId = null
    }

    fun clearAllActive() {
        activeGames = emptyList()
        Store.saveActiveGames(ctx, emptyList())
        currentActiveGameId = null
    }

    fun saveCurrent(type: GameType, engineJson: String, bots: Map<String, BotLevel>, players: List<Player>) {
        val id = currentActiveGameId ?: UUID.randomUUID().toString()
        currentActiveGameId = id
        val existing = activeGames.find { it.id == id }
        val saved = SavedGame(
            id = id,
            date = existing?.date ?: System.currentTimeMillis(),
            type = type,
            summary = type.pl,
            engineJson = engineJson,
            bots = bots,
            players = players,
            progress = emptyList(),
            inProgress = true
        )
        activeGames = listOf(saved) + activeGames.filterNot { it.id == id }
        Store.saveActiveGames(ctx, activeGames)
    }

    fun loadSaved(): SavedGame? = activeGames.firstOrNull()

    fun clearSaved() {
        val curId = currentActiveGameId
        if (curId != null) {
            removeActive(curId)
            currentActiveGameId = null
        }
        Store.clearSavedGame(ctx)
    }
}

@Composable
fun App(ctx: Context) {
    val state = remember { AppState(ctx) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main(MainTab.START)) }
    val audio = remember { GameAudio(ctx) }
    val tts = remember { GameTts(ctx, state.settings.speechRate, state.settings.pitch) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    var activeUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        if (state.settings.autoCheckUpdates) {
            try {
                val targetUrl = state.settings.updateUrl.ifBlank { UpdateManager.DEFAULT_UPDATE_URL }
                val res = UpdateManager.checkUpdate(targetUrl, BuildConfig.VERSION_CODE)
                if (res.isSuccess) {
                    val info = res.getOrNull()
                    if (info != null) {
                        activeUpdateInfo = info
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun autoSaveFinishedGame(engine: Any) {
        val data = finishDataFor(engine)
        val names = data.players.map { it.name }
        val perPlayer = data.players.indices.associate { i -> names[i] to data.perfs.getOrElse(i) { Perf() } }
        val record = GameRecord(
            type = data.type,
            players = names,
            winner = if (data.finishedOrder.isNotEmpty() && data.finishedOrder[0] < names.size) names[data.finishedOrder[0]] else "",
            places = data.finishedOrder.filter { it < names.size }.map { names[it] },
            summary = data.summary,
            perPlayer = perPlayer
        )
        // Add to games if not already present
        if (state.games.none { it.date == record.date && it.summary == record.summary }) {
            state.addGame(record)
        }
        val curId = state.currentActiveGameId
        if (curId != null) {
            state.removeActive(curId)
            state.currentActiveGameId = null
        }
    }

    fun resumeSaved(saved: SavedGame) {
        state.currentActiveGameId = saved.id
        val engineObj = JSONObject(saved.engineJson)
        when (saved.type) {
            GameType.X01 -> currentScreen = Screen.PlayX01(X01Engine.fromJson(engineObj, saved.players, saved.bots))
            GameType.CRICKET -> currentScreen = Screen.PlayCricket(CricketEngine.fromJson(engineObj, saved.players, saved.bots))
            GameType.AROUND -> currentScreen = Screen.PlayAround(AroundEngine.fromJson(engineObj, saved.players, saved.bots))
        }
    }

    fun finishSaved(saved: SavedGame) {
        state.currentActiveGameId = saved.id
        val engineObj = JSONObject(saved.engineJson)
        val engine: Any = when (saved.type) {
            GameType.X01 -> X01Engine.fromJson(engineObj, saved.players, saved.bots)
            GameType.CRICKET -> CricketEngine.fromJson(engineObj, saved.players, saved.bots)
            GameType.AROUND -> AroundEngine.fromJson(engineObj, saved.players, saved.bots)
        }
        when (engine) {
            is X01Engine -> engine.finishNow()
            is CricketEngine -> engine.finishNow()
            is AroundEngine -> engine.finishNow()
        }
        autoSaveFinishedGame(engine)
        currentScreen = Screen.Finish(finishDataFor(engine), engine)
    }

    BackHandler(
        enabled = currentScreen !is Screen.PlayX01 &&
                  currentScreen !is Screen.PlayCricket &&
                  currentScreen !is Screen.PlayAround
    ) {
        when (val s = currentScreen) {
            is Screen.Checkout -> {
                currentScreen = Screen.Main(MainTab.SETTINGS)
            }
            is Screen.StatAfter -> {
                currentScreen = Screen.Finish(s.data, s.engine)
            }
            is Screen.Finish -> {
                currentScreen = Screen.Main(MainTab.START)
            }
            is Screen.Main -> {
                if (s.tab != MainTab.START) {
                    currentScreen = Screen.Main(MainTab.START)
                }
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DartDarkBg,
        contentColor = Color.White
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * state.settings.fontScale)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .statusBarsPadding()
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val s = currentScreen) {
                        is Screen.Main -> {
                            when (s.tab) {
                                MainTab.START -> StartScreen(
                                    state = state,
                                    onStartGame = {
                                        val newId = state.currentActiveGameId ?: java.util.UUID.randomUUID().toString()
                                        state.currentActiveGameId = newId
                                        when (it) {
                                            is Screen.PlayX01 -> state.saveActive(it.engine, newId)
                                            is Screen.PlayCricket -> state.saveActive(it.engine, newId)
                                            is Screen.PlayAround -> state.saveActive(it.engine, newId)
                                            else -> {}
                                        }
                                        currentScreen = it
                                    },
                                    onResumeGame = { resumeSaved(it) },
                                    onDeleteSavedGame = { state.removeActive(it) },
                                    onFinishSavedGame = { finishSaved(it) }
                                )
                                MainTab.PLAYERS -> PlayersScreen(state = state)
                                MainTab.STATS -> StatsScreen(state = state)
                                MainTab.HISTORY -> HistoryScreen(
                                    state = state,
                                    onResume = { resumeSaved(it) },
                                    onDeleteActive = { state.removeActive(it) },
                                    onFinishActive = { finishSaved(it) }
                                )
                                MainTab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    tts = tts,
                                    audio = audio,
                                    onOpenCheckouts = { currentScreen = Screen.Checkout }
                                )
                            }
                        }
                        is Screen.PlayX01 -> X01Screen(
                            engine = s.engine,
                            state = state,
                            audio = audio,
                            tts = tts,
                            onExit = {
                                currentScreen = Screen.Main(MainTab.START)
                            },
                            onFinish = { engine ->
                                autoSaveFinishedGame(engine)
                                currentScreen = Screen.Finish(finishDataFor(engine), engine)
                            }
                        )
                        is Screen.PlayCricket -> CricketScreen(
                            engine = s.engine,
                            state = state,
                            audio = audio,
                            tts = tts,
                            onExit = {
                                currentScreen = Screen.Main(MainTab.START)
                            },
                            onFinish = { engine ->
                                autoSaveFinishedGame(engine)
                                currentScreen = Screen.Finish(finishDataFor(engine), engine)
                            }
                        )
                        is Screen.PlayAround -> AroundScreen(
                            engine = s.engine,
                            state = state,
                            audio = audio,
                            tts = tts,
                            onExit = {
                                currentScreen = Screen.Main(MainTab.START)
                            },
                            onFinish = { engine ->
                                autoSaveFinishedGame(engine)
                                currentScreen = Screen.Finish(finishDataFor(engine), engine)
                            }
                        )
                        is Screen.Checkout -> CheckoutScreen(
                            onBack = { currentScreen = Screen.Main(MainTab.SETTINGS) }
                        )
                        is Screen.Finish -> FinishScreen(
                            data = s.data,
                            state = state,
                            audio = audio,
                            onUndo = {
                                when (val e = s.engine) {
                                    is X01Engine -> { e.undo(); currentScreen = Screen.PlayX01(e) }
                                    is CricketEngine -> { e.undo(); currentScreen = Screen.PlayCricket(e) }
                                    is AroundEngine -> { e.undo(); currentScreen = Screen.PlayAround(e) }
                                }
                            },
                            onSave = {
                                autoSaveFinishedGame(s.engine)
                                currentScreen = Screen.Main(MainTab.START)
                            },
                            onDontSave = {
                                val data = s.data
                                state.games = state.games.filterNot { it.summary == data.summary && it.type == data.type }
                                Store.saveGames(state.ctx, state.games)
                                val curId = state.currentActiveGameId
                                if (curId != null) state.removeActive(curId)
                                currentScreen = Screen.Main(MainTab.START)
                            },
                            onStatistics = { currentScreen = Screen.StatAfter(s.data, s.engine) }
                        )
                        is Screen.StatAfter -> StatAfterScreen(
                            data = s.data,
                            engine = s.engine,
                            onDone = { currentScreen = Screen.Finish(s.data, s.engine) }
                        )
                    }
                }

                // Persistent Bottom Navigation Bar on Main Screens
                if (currentScreen is Screen.Main) {
                    val activeTab = (currentScreen as Screen.Main).tab
                    MainBottomBar(
                        selectedTab = activeTab,
                        onSelectTab = { currentScreen = Screen.Main(it) }
                    )
                }
            }
        }
    }

    if (activeUpdateInfo != null) {
        UpdateDialog(
            info = activeUpdateInfo!!,
            onDismiss = { activeUpdateInfo = null }
        )
    }
}
