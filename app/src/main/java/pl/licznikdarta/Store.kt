package pl.licznikdarta

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Store {
    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun loadPlayers(ctx: Context): List<Player> = try {
        val arr = JSONArray(file(ctx, "players.json").readText().ifEmpty { "[]" })
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Player(o.getString("id"), o.getString("name"))
        }
    } catch (e: Exception) { emptyList() }

    fun savePlayers(ctx: Context, players: List<Player>) {
        val arr = JSONArray()
        players.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        file(ctx, "players.json").writeText(arr.toString())
    }

    fun loadGames(ctx: Context): List<GameRecord> = try {
        val arr = JSONArray(file(ctx, "games.json").readText().ifEmpty { "[]" })
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val pp = mutableMapOf<String, Perf>()
            val ppO = o.optJSONObject("perPlayer")
            ppO?.keys()?.forEach { k ->
                val p = ppO.getJSONObject(k)
                val hits = mutableMapOf<String, Int>()
                p.optJSONObject("hits")?.keys()?.forEach { hk -> hits[hk] = p.getJSONObject("hits").getInt(hk) }
                pp[k] = Perf(
                    throws = p.optInt("throws"),
                    points = p.optInt("points"),
                    bestTurn = p.optInt("bestTurn"),
                    first9 = p.optInt("first9"),
                    marks = p.optInt("marks"),
                    doubles = p.optInt("doubles"),
                    triples = p.optInt("triples"),
                    legsWon = p.optInt("legsWon"),
                    legsPlayed = p.optInt("legsPlayed"),
                    maxCheckout = p.optInt("maxCheckout"),
                    minDartsLeg = p.optInt("minDartsLeg"),
                    p60 = p.optInt("p60"),
                    p100 = p.optInt("p100"),
                    p140 = p.optInt("p140"),
                    p180 = p.optInt("p180"),
                    checkoutAttempts = p.optInt("checkoutAttempts"),
                    hitsBySector = hits
                )
            }
            GameRecord(
                id = o.getString("id"),
                type = GameType.valueOf(o.getString("type")),
                date = o.getLong("date"),
                players = (0 until o.getJSONArray("players").length()).map { o.getJSONArray("players").getString(it) },
                winner = o.getString("winner"),
                places = (0 until o.optJSONArray("places").let { it?.length() ?: 0 }).map { o.getJSONArray("places").getString(it) },
                summary = o.getString("summary"),
                perPlayer = pp
            )
        }
    } catch (e: Exception) { emptyList() }

    fun saveGames(ctx: Context, games: List<GameRecord>) {
        val list = games.toList()
        Thread {
            try {
                val arr = JSONArray()
                list.forEach { g ->
                    val pp = JSONObject()
                    g.perPlayer.forEach { (k, v) ->
                        val hits = JSONObject(); v.hitsBySector.forEach { (hk, hv) -> hits.put(hk, hv) }
                        pp.put(k, JSONObject()
                            .put("throws", v.throws)
                            .put("points", v.points)
                            .put("bestTurn", v.bestTurn)
                            .put("first9", v.first9)
                            .put("marks", v.marks)
                            .put("doubles", v.doubles)
                            .put("triples", v.triples)
                            .put("legsWon", v.legsWon)
                            .put("legsPlayed", v.legsPlayed)
                            .put("maxCheckout", v.maxCheckout)
                            .put("minDartsLeg", v.minDartsLeg)
                            .put("p60", v.p60)
                            .put("p100", v.p100)
                            .put("p140", v.p140)
                            .put("p180", v.p180)
                            .put("checkoutAttempts", v.checkoutAttempts)
                            .put("hits", hits))
                    }
                    arr.put(JSONObject().put("id", g.id).put("type", g.type.name).put("date", g.date)
                        .put("players", JSONArray(g.players)).put("winner", g.winner)
                        .put("places", JSONArray(g.places))
                        .put("summary", g.summary).put("perPlayer", pp))
                }
                file(ctx, "games.json").writeText(arr.toString())
            } catch (_: Exception) {}
        }.start()
    }

    fun loadSettings(ctx: Context): Settings = try {
        val o = JSONObject(file(ctx, "settings.json").readText())
        Settings(
            sounds = o.optBoolean("sounds", true),
            voice = o.optBoolean("voice", false),
            botLevel = try { BotLevel.valueOf(o.optString("botLevel", BotLevel.NORMAL.name)) } catch (_: Exception) { BotLevel.NORMAL },
            speechRate = o.optDouble("speechRate", 1.0).toFloat(),
            pitch = o.optDouble("pitch", 1.0).toFloat(),
            fontScale = o.optDouble("fontScale", 1.0).toFloat(),
            keySound = try { KeySound.valueOf(o.optString("keySound", KeySound.MECHANICAL.name)) } catch (_: Exception) { KeySound.MECHANICAL },
            autoCheckUpdates = o.optBoolean("autoCheckUpdates", true),
            updateUrl = o.optString("updateUrl", "")
        )
    } catch (_: Exception) { Settings() }

    fun saveSettings(ctx: Context, s: Settings) {
        file(ctx, "settings.json").writeText(JSONObject()
            .put("sounds", s.sounds)
            .put("voice", s.voice)
            .put("botLevel", s.botLevel.name)
            .put("speechRate", s.speechRate.toDouble())
            .put("pitch", s.pitch.toDouble())
            .put("fontScale", s.fontScale.toDouble())
            .put("keySound", s.keySound.name)
            .put("autoCheckUpdates", s.autoCheckUpdates)
            .put("updateUrl", s.updateUrl).toString())
    }

    fun loadLastSetup(ctx: Context): LastGameSetup = try {
        val f = file(ctx, "last_setup.json")
        if (!f.exists()) LastGameSetup() else {
            val o = JSONObject(f.readText())
            val lineupArr = o.optJSONArray("lineup")
            val lineupList = if (lineupArr != null) (0 until lineupArr.length()).map { lineupArr.getString(it) } else emptyList()
            LastGameSetup(
                type = try { GameType.valueOf(o.optString("type", GameType.X01.name)) } catch (_: Exception) { GameType.X01 },
                x01Points = o.optInt("x01Points", 501),
                x01CheckIn = try { CheckInMode.valueOf(o.optString("x01CheckIn", CheckInMode.STRAIGHT.name)) } catch (_: Exception) { CheckInMode.STRAIGHT },
                x01CheckOut = try { CheckOutMode.valueOf(o.optString("x01CheckOut", CheckOutMode.DOUBLE.name)) } catch (_: Exception) { CheckOutMode.DOUBLE },
                x01SetLegMode = try { SetLegMode.valueOf(o.optString("x01SetLegMode", SetLegMode.FIRST_TO.name)) } catch (_: Exception) { SetLegMode.FIRST_TO },
                x01Sets = o.optInt("x01Sets", 1),
                x01Legs = o.optInt("x01Legs", 1),
                cricketPointsOn = o.optBoolean("cricketPointsOn", true),
                cricketCutThroat = o.optBoolean("cricketCutThroat", false),
                cricketSetLegMode = try { SetLegMode.valueOf(o.optString("cricketSetLegMode", SetLegMode.FIRST_TO.name)) } catch (_: Exception) { SetLegMode.FIRST_TO },
                cricketSets = o.optInt("cricketSets", 1),
                cricketLegs = o.optInt("cricketLegs", 1),
                randomOrder = o.optBoolean("randomOrder", false),
                useBot = o.optBoolean("useBot", false),
                botLevel = try { BotLevel.valueOf(o.optString("botLevel", BotLevel.NORMAL.name)) } catch (_: Exception) { BotLevel.NORMAL },
                lineup = lineupList
            )
        }
    } catch (_: Exception) { LastGameSetup() }

    fun saveLastSetup(ctx: Context, s: LastGameSetup) {
        Thread {
            try {
                val o = JSONObject()
                    .put("type", s.type.name)
                    .put("x01Points", s.x01Points)
                    .put("x01CheckIn", s.x01CheckIn.name)
                    .put("x01CheckOut", s.x01CheckOut.name)
                    .put("x01SetLegMode", s.x01SetLegMode.name)
                    .put("x01Sets", s.x01Sets)
                    .put("x01Legs", s.x01Legs)
                    .put("cricketPointsOn", s.cricketPointsOn)
                    .put("cricketCutThroat", s.cricketCutThroat)
                    .put("cricketSetLegMode", s.cricketSetLegMode.name)
                    .put("cricketSets", s.cricketSets)
                    .put("cricketLegs", s.cricketLegs)
                    .put("randomOrder", s.randomOrder)
                    .put("useBot", s.useBot)
                    .put("botLevel", s.botLevel.name)
                    .put("lineup", JSONArray(s.lineup))
                file(ctx, "last_setup.json").writeText(o.toString())
            } catch (_: Exception) {}
        }.start()
    }

    fun saveSavedGame(ctx: Context, json: String) {
        file(ctx, "saved_game.json").writeText(json)
    }

    fun loadSavedGame(ctx: Context): JSONObject? = try {
        JSONObject(file(ctx, "saved_game.json").readText())
    } catch (e: Exception) { null }

    fun clearSavedGame(ctx: Context) {
        file(ctx, "saved_game.json").delete()
    }

    fun loadLastLineup(ctx: Context): List<String> = try {
        val arr = JSONArray(file(ctx, "last_lineup.json").readText().ifEmpty { "[]" })
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun saveLastLineup(ctx: Context, lineup: List<String>) {
        try {
            val arr = JSONArray()
            lineup.forEach { arr.put(it) }
            file(ctx, "last_lineup.json").writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun loadActiveGames(ctx: Context): List<SavedGame> = try {
        val f = file(ctx, "active_games.json")
        val content = if (f.exists()) f.readText().ifEmpty { "[]" } else "[]"
        val arr = JSONArray(content)
        val list = mutableListOf<SavedGame>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val bots = mutableMapOf<String, BotLevel>()
            val bo = o.optJSONObject("bots")
            bo?.keys()?.forEach { k ->
                bots[k] = try { BotLevel.valueOf(bo.getString(k)) } catch (_: Exception) { BotLevel.NORMAL }
            }
            val players = mutableListOf<Player>()
            val pa = o.optJSONArray("players")
            if (pa != null) {
                for (pIdx in 0 until pa.length()) {
                    val po = pa.getJSONObject(pIdx)
                    players.add(Player(po.getString("id"), po.getString("name")))
                }
            }
            val progressList = mutableListOf<PlayerProgress>()
            val prArr = o.optJSONArray("progress")
            if (prArr != null) {
                for (prIdx in 0 until prArr.length()) {
                    val pro = prArr.getJSONObject(prIdx)
                    progressList.add(
                        PlayerProgress(
                            name = pro.getString("name"),
                            score = pro.getInt("score"),
                            setsWon = pro.optInt("setsWon", 0),
                            legsWon = pro.optInt("legsWon", 0)
                        )
                    )
                }
            }
            list.add(
                SavedGame(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    date = o.optLong("date", System.currentTimeMillis()),
                    type = GameType.valueOf(o.getString("type")),
                    summary = o.optString("summary", ""),
                    engineJson = o.getJSONObject("engine").toString(),
                    bots = bots,
                    players = players,
                    progress = progressList,
                    inProgress = o.optBoolean("inProgress", true)
                )
            )
        }
        if (list.isEmpty()) {
            val legacy = loadSavedGame(ctx)
            if (legacy != null) {
                val bots = mutableMapOf<String, BotLevel>()
                val bo = legacy.optJSONObject("bots")
                bo?.keys()?.forEach { k ->
                    bots[k] = try { BotLevel.valueOf(bo.getString(k)) } catch (_: Exception) { BotLevel.NORMAL }
                }
                val players = mutableListOf<Player>()
                val pa = legacy.optJSONArray("players")
                if (pa != null) for (pIdx in 0 until pa.length()) {
                    players.add(Player(pa.getJSONObject(pIdx).getString("id"), pa.getJSONObject(pIdx).getString("name")))
                }
                val legacyType = GameType.valueOf(legacy.getString("type"))
                val migrated = SavedGame(
                    id = java.util.UUID.randomUUID().toString(),
                    date = System.currentTimeMillis(),
                    type = legacyType,
                    summary = legacyType.pl,
                    engineJson = legacy.getJSONObject("engine").toString(),
                    bots = bots,
                    players = players,
                    progress = emptyList(),
                    inProgress = true
                )
                list.add(migrated)
                saveActiveGames(ctx, list)
            }
        }
        list
    } catch (e: Exception) { emptyList() }

    fun saveActiveGames(ctx: Context, games: List<SavedGame>) {
        val list = games.toList()
        Thread {
            try {
                val arr = JSONArray()
                list.forEach { g ->
                    val b = JSONObject()
                    g.bots.forEach { (k, v) -> b.put(k, v.name) }
                    val pa = JSONArray()
                    g.players.forEach { pa.put(JSONObject().put("id", it.id).put("name", it.name)) }
                    val prArr = JSONArray()
                    g.progress.forEach { pr ->
                        prArr.put(
                            JSONObject()
                                .put("name", pr.name)
                                .put("score", pr.score)
                                .put("setsWon", pr.setsWon)
                                .put("legsWon", pr.legsWon)
                        )
                    }
                    arr.put(
                        JSONObject()
                            .put("id", g.id)
                            .put("date", g.date)
                            .put("type", g.type.name)
                            .put("summary", g.summary)
                            .put("engine", JSONObject(g.engineJson))
                            .put("bots", b)
                            .put("players", pa)
                            .put("progress", prArr)
                            .put("inProgress", g.inProgress)
                    )
                }
                file(ctx, "active_games.json").writeText(arr.toString())
            } catch (_: Exception) {}
        }.start()
    }

    fun exportDatabase(ctx: Context): String {
        val backup = JSONObject()
        backup.put("version", 1)
        backup.put("exportDate", System.currentTimeMillis())

        val playersF = file(ctx, "players.json")
        if (playersF.exists()) backup.put("players", JSONArray(playersF.readText().ifEmpty { "[]" }))

        val gamesF = file(ctx, "games.json")
        if (gamesF.exists()) backup.put("games", JSONArray(gamesF.readText().ifEmpty { "[]" }))

        val settingsF = file(ctx, "settings.json")
        if (settingsF.exists()) backup.put("settings", JSONObject(settingsF.readText().ifEmpty { "{}" }))

        val activeF = file(ctx, "active_games.json")
        if (activeF.exists()) backup.put("active_games", JSONArray(activeF.readText().ifEmpty { "[]" }))

        val lineupF = file(ctx, "last_lineup.json")
        if (lineupF.exists()) backup.put("last_lineup", JSONArray(lineupF.readText().ifEmpty { "[]" }))

        return backup.toString(2)
    }

    fun importDatabase(ctx: Context, jsonStr: String): Boolean {
        return try {
            val backup = JSONObject(jsonStr)
            if (backup.has("players")) {
                file(ctx, "players.json").writeText(backup.getJSONArray("players").toString())
            }
            if (backup.has("games")) {
                file(ctx, "games.json").writeText(backup.getJSONArray("games").toString())
            }
            if (backup.has("settings")) {
                file(ctx, "settings.json").writeText(backup.getJSONObject("settings").toString())
            }
            if (backup.has("active_games")) {
                file(ctx, "active_games.json").writeText(backup.getJSONArray("active_games").toString())
            }
            if (backup.has("last_lineup")) {
                file(ctx, "last_lineup.json").writeText(backup.getJSONArray("last_lineup").toString())
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
