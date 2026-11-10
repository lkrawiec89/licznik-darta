package pl.licznikdarta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class TurnOutcome(val newScore: Int, val checkedIn: Boolean, val busted: Boolean, val won: Boolean)

/** Cricket rule helpers shared by standard, no-points, and Cut Throat games. */
internal object CricketRules {
    fun marksAdded(marksBefore: Int, marksHit: Int): Int =
        minOf(marksHit.coerceAtLeast(0), (3 - marksBefore).coerceAtLeast(0))

    fun excessMarks(marksBefore: Int, marksHit: Int): Int =
        (marksBefore + marksHit.coerceAtLeast(0) - 3).coerceAtLeast(0)

    /** The winner has closed every target and has the best score among all players. */
    fun winnerIndex(
        marksByPlayer: List<List<Int>>,
        pointsByPlayer: List<Int>,
        pointsOn: Boolean,
        cutThroat: Boolean,
        excludePlayers: Collection<Int> = emptyList()
    ): Int? {
        val activePlayers = marksByPlayer.indices.filter { it !in excludePlayers }
        if (activePlayers.isEmpty()) return null
        val closedPlayers = activePlayers.filter { player ->
            marksByPlayer[player].all { it >= 3 }
        }
        if (closedPlayers.isEmpty()) return null
        if (!pointsOn) return closedPlayers.first()

        val winningScore = if (cutThroat) {
            activePlayers.minOf { pointsByPlayer[it] }
        } else {
            activePlayers.maxOf { pointsByPlayer[it] }
        }
        return closedPlayers.firstOrNull { pointsByPlayer[it] == winningScore }
    }
}

fun simulateX01Turn(startScore: Int, startIn: Boolean, throws: List<DartThrow>, checkIn: CheckInMode, checkOut: CheckOutMode): TurnOutcome {
    var score = startScore
    var playing = startIn
    for (t in throws) {
        if (!playing && checkIn != CheckInMode.STRAIGHT) {
            val valid = when (checkIn) {
                CheckInMode.DOUBLE -> t.isDouble
                CheckInMode.MASTER -> t.isDouble || t.isTriple
                else -> true
            }
            if (!valid) continue
            playing = true
        }
        score -= t.value
        val lastValid = when (checkOut) {
            CheckOutMode.STRAIGHT -> true
            CheckOutMode.DOUBLE -> t.isDouble
            CheckOutMode.MASTER -> t.isDouble || t.isTriple
        }
        if (score < 0 || (score == 1 && checkOut != CheckOutMode.STRAIGHT)) {
            return TurnOutcome(startScore, startIn, true, false)
        }
        if (score == 0) {
            return if (lastValid) TurnOutcome(0, playing, false, true) else TurnOutcome(startScore, startIn, true, false)
        }
    }
    return TurnOutcome(score, playing, false, false)
}

private fun legWinCondition(won: Int, target: Int, mode: SetLegMode) =
    if (mode == SetLegMode.FIRST_TO) won == target else won > target / 2

private fun setWinCondition(won: Int, target: Int, mode: SetLegMode) =
    if (mode == SetLegMode.FIRST_TO) won == target else won > target / 2

class X01Engine(
    val players: List<Player>,
    val bots: Map<String, BotLevel>,
    val startScore: Int,
    val checkIn: CheckInMode,
    val checkOut: CheckOutMode,
    val legsTarget: Int,
    val setsTarget: Int,
    val setLegMode: SetLegMode
) {
    val scores = mutableStateListOf<Int>()
    val checkedIn = mutableStateListOf<Boolean>()
    val legsWonInSet = mutableStateListOf<Int>()
    val setsWon = mutableStateListOf<Int>()
    val legsWonTotal = mutableStateListOf<Int>()
    var current by mutableStateOf(0)
    var dartsUsed by mutableStateOf(0)
    var legNumber by mutableStateOf(0)
    var setNumber by mutableStateOf(0)
    var starterIdx by mutableStateOf(0)
    var turnCounter by mutableStateOf(0)
    var message by mutableStateOf("")
    var spoken by mutableStateOf("")
    var finished by mutableStateOf(false)
    var lastThrowBust by mutableStateOf(false)
    var lastTurnThreeZeros by mutableStateOf(false)
    val finishedOrder = mutableStateListOf<Int>()
    var pendingFinishIdx by mutableStateOf(-1)
    val turnThrows = mutableStateListOf<DartThrow>()
    val playerLastTurnThrows = mutableStateListOf<List<DartThrow>>()
    val playerLastTurnBusted = mutableStateListOf<Boolean>()

    private val dartLog = mutableListOf<DartThrow>()
    private val dartMeta = mutableListOf<IntArray>() // [playerIdx, turnCounter, leg, set]
    private val dartContrib = mutableListOf<Int>() // rzeczywisty zysk z lotki (0 przy buscie/check-inie)
    private var turnStartScore = 0
    private var turnStartCheckedIn = false
    private val snapshots = mutableListOf<JSONObject>()
    val turnsLog = mutableListOf<Turn>()
    private val dartsInLeg = mutableListOf<Int>()
    private val doubles = mutableListOf<Int>()
    private val triples = mutableListOf<Int>()
    private val maxCheckout = mutableListOf<Int>()
    private val minDartsLeg = mutableListOf<Int>()
    private val sectorHits = mutableListOf<MutableMap<String, Int>>()

    init {
        players.forEach {
            scores.add(startScore)
            checkedIn.add(checkIn == CheckInMode.STRAIGHT)
            legsWonInSet.add(0); setsWon.add(0); legsWonTotal.add(0)
            dartsInLeg.add(0); doubles.add(0); triples.add(0); maxCheckout.add(0); minDartsLeg.add(Int.MAX_VALUE)
            sectorHits.add(mutableMapOf())
            playerLastTurnThrows.add(emptyList())
            playerLastTurnBusted.add(false)
        }
    }

    fun isBot(idx: Int) = (bots[players[idx].id] ?: BotLevel.NONE) != BotLevel.NONE

    fun isFinishedPlayer(idx: Int) = finishedOrder.contains(idx)

    fun activePlayers(): List<Int> = players.indices.filter { !isFinishedPlayer(it) }

    fun remainingFor(idx: Int): Int = scores[idx]

    fun currentCheckoutHint(): String? {
        if (finished) return null
        val idx = current
        val remDarts = 3 - dartsUsed
        if (remDarts <= 0) return null

        if (dartsUsed > 0 && turnThrows.isNotEmpty()) {
            val initialCombo = Checkouts.solve(turnStartScore, 3, checkOut, turnStartCheckedIn, checkIn)
            if (initialCombo != null && initialCombo.size >= dartsUsed) {
                val followedPlan = (0 until dartsUsed).all { i ->
                    val actual = turnThrows.getOrNull(i)
                    val expected = initialCombo.getOrNull(i)
                    actual != null && expected != null && actual.value == expected.value && actual.mult == expected.mult
                }
                if (followedPlan) {
                    val remainingPlan = initialCombo.drop(dartsUsed)
                    if (remainingPlan.isNotEmpty()) {
                        return remainingPlan.joinToString(" → ") { it.label() }
                    }
                }
            }
        }

        return Checkouts.hintText(scores[idx], remDarts, checkOut, checkedIn[idx], checkIn)
    }

    private fun snapshot(): JSONObject {
        val o = JSONObject()
        o.put("current", current).put("darts", dartsUsed).put("leg", legNumber).put("set", setNumber)
        o.put("starter", starterIdx).put("turnCounter", turnCounter).put("pending", pendingFinishIdx)
        o.put("finished", finished)
        o.put("scores", JSONArray(scores)).put("checkedIn", JSONArray(checkedIn))
        o.put("legsWonInSet", JSONArray(legsWonInSet)).put("setsWon", JSONArray(setsWon))
        o.put("legsWonTotal", JSONArray(legsWonTotal))
        o.put("finishedOrder", JSONArray(finishedOrder))
        o.put("dartLogSize", dartLog.size).put("turnsLogSize", turnsLog.size)
        o.put("dartsInLeg", JSONArray(dartsInLeg))
        o.put("doubles", JSONArray(doubles)).put("triples", JSONArray(triples))
        o.put("maxCheckout", JSONArray(maxCheckout)).put("minDartsLeg", JSONArray(minDartsLeg))
        val sh = JSONArray()
        sectorHits.forEach { m ->
            val mo = JSONObject(); m.forEach { (k, v) -> mo.put(k, v) }
            sh.put(mo)
        }
        o.put("sectorHits", sh)
        o.put("message", message)
        o.put("turnStartScore", turnStartScore).put("turnStartCheckedIn", turnStartCheckedIn)
        return o
    }

    private fun restore(o: JSONObject) {
        current = o.getInt("current"); dartsUsed = o.getInt("darts")
        legNumber = o.getInt("leg"); setNumber = o.getInt("set")
        starterIdx = o.getInt("starter"); turnCounter = o.getInt("turnCounter")
        pendingFinishIdx = o.getInt("pending"); finished = o.getBoolean("finished")
        scores.clear(); checkedIn.clear(); legsWonInSet.clear(); setsWon.clear(); legsWonTotal.clear(); finishedOrder.clear()
        dartsInLeg.clear(); doubles.clear(); triples.clear(); maxCheckout.clear(); minDartsLeg.clear(); sectorHits.clear()
        val sa = o.getJSONArray("scores"); for (i in 0 until sa.length()) scores.add(sa.getInt(i))
        val ca = o.getJSONArray("checkedIn"); for (i in 0 until ca.length()) checkedIn.add(ca.getBoolean(i))
        val la = o.getJSONArray("legsWonInSet"); for (i in 0 until la.length()) legsWonInSet.add(la.getInt(i))
        val st = o.getJSONArray("setsWon"); for (i in 0 until st.length()) setsWon.add(st.getInt(i))
        val lt = o.getJSONArray("legsWonTotal"); for (i in 0 until lt.length()) legsWonTotal.add(lt.getInt(i))
        val fo = o.getJSONArray("finishedOrder"); for (i in 0 until fo.length()) finishedOrder.add(fo.getInt(i))
        val da = o.getJSONArray("dartsInLeg"); for (i in 0 until da.length()) dartsInLeg.add(da.getInt(i))
        val doa = o.getJSONArray("doubles"); for (i in 0 until doa.length()) doubles.add(doa.getInt(i))
        val ta = o.getJSONArray("triples"); for (i in 0 until ta.length()) triples.add(ta.getInt(i))
        val ma = o.getJSONArray("maxCheckout"); for (i in 0 until ma.length()) maxCheckout.add(ma.getInt(i))
        val mda = o.getJSONArray("minDartsLeg"); for (i in 0 until mda.length()) minDartsLeg.add(mda.getInt(i))
        val sh = o.getJSONArray("sectorHits")
        for (i in 0 until sh.length()) {
            val mo = sh.getJSONObject(i); val m = mutableMapOf<String, Int>()
            mo.keys().forEach { k -> m[k] = mo.getInt(k) }
            sectorHits.add(m)
        }
        while (dartLog.size > o.getInt("dartLogSize")) dartLog.removeAt(dartLog.size - 1)
        while (dartMeta.size > o.getInt("dartLogSize")) dartMeta.removeAt(dartMeta.size - 1)
        while (dartContrib.size > o.getInt("dartLogSize")) dartContrib.removeAt(dartContrib.size - 1)
        while (turnsLog.size > o.getInt("turnsLogSize")) turnsLog.removeAt(turnsLog.size - 1)
        refreshTurnThrows()
        for (i in players.indices) {
            if (i < playerLastTurnThrows.size) playerLastTurnThrows[i] = emptyList()
            if (i < playerLastTurnBusted.size) playerLastTurnBusted[i] = false
        }
        message = o.getString("message")
        turnStartScore = o.optInt("turnStartScore", scores.getOrNull(current) ?: 0)
        turnStartCheckedIn = o.optBoolean("turnStartCheckedIn", checkedIn.getOrNull(current) ?: false)
    }

    private fun refreshTurnThrows() {
        turnThrows.clear()
        dartMeta.forEachIndexed { i, meta ->
            if (meta[0] == current && meta[1] == turnCounter) turnThrows.add(dartLog[i])
        }
    }

    fun submitDart(t: DartThrow) {
        if (finished || dartsUsed >= 3) return
        lastThrowBust = false
        spoken = ""
        snapshots.add(snapshot())
        dartLog.add(t)
        dartMeta.add(intArrayOf(current, turnCounter, legNumber, setNumber))
        val idx = current
        val preScore = scores[idx]
        if (dartsUsed == 0) {
            turnStartScore = preScore
            turnStartCheckedIn = checkedIn[idx]
            lastThrowBust = false
            lastTurnThreeZeros = false
            if (idx < playerLastTurnBusted.size) playerLastTurnBusted[idx] = false
            if (idx < playerLastTurnThrows.size) playerLastTurnThrows[idx] = emptyList()
        }
        val outcome = simulateX01Turn(preScore, checkedIn[idx], listOf(t), checkIn, checkOut)
        scores[idx] = outcome.newScore
        checkedIn[idx] = outcome.checkedIn
        dartsInLeg[idx]++
        when {
            t.isMiss -> {}
            t.mult == 2 -> doubles[idx] = doubles[idx] + 1
            t.mult == 3 -> triples[idx] = triples[idx] + 1
            else -> {}
        }
        val label = t.label()
        if (!t.isMiss) sectorHits[idx][label] = (sectorHits[idx][label] ?: 0) + 1
        val gained = if (outcome.busted) 0 else preScore - outcome.newScore
        dartContrib.add(gained)
        val turnSum = turnThrows.sumOf { it.value } + t.value
        message = "${players[idx].name}: ${t.label()}"
        if (outcome.busted) {
            lastThrowBust = true
            message = "${players[idx].name}: BUST!"
            spoken = "0"
            for (i in dartContrib.indices) {
                val meta = dartMeta[i]
                if (meta[0] == idx && meta[1] == turnCounter) dartContrib[i] = 0
            }
            scores[idx] = turnStartScore
            checkedIn[idx] = turnStartCheckedIn
            val currentTurnDarts = mutableListOf<DartThrow>()
            dartMeta.forEachIndexed { i, meta ->
                if (meta[0] == idx && meta[1] == turnCounter) currentTurnDarts.add(dartLog[i])
            }
            if (idx < playerLastTurnThrows.size) playerLastTurnThrows[idx] = currentTurnDarts
            if (idx < playerLastTurnBusted.size) playerLastTurnBusted[idx] = true
            endTurn()
            return
        }
        if (outcome.won) {
            maxCheckout[idx] = maxOf(maxCheckout[idx], t.value)
            minDartsLeg[idx] = minOf(minDartsLeg[idx], dartsInLeg[idx])
            spoken = turnSum.toString()
            recordTurn()
            onLegWon(idx)
            return
        }
        dartsUsed++
        refreshTurnThrows()
        if (dartsUsed >= 3) {
            spoken = turnSum.toString()
            lastTurnThreeZeros = (turnThrows.size == 3 && turnThrows.all { it.value == 0 })
            endTurn()
        }
    }

    private fun endTurn() {
        recordTurn()
        val prevIdx = current
        val currentTurnDarts = mutableListOf<DartThrow>()
        dartMeta.forEachIndexed { i, meta ->
            if (meta[0] == prevIdx && meta[1] == turnCounter) currentTurnDarts.add(dartLog[i])
        }
        if (prevIdx < playerLastTurnThrows.size && !playerLastTurnBusted[prevIdx]) {
            playerLastTurnThrows[prevIdx] = currentTurnDarts
        }
        dartsUsed = 0
        turnCounter++
        current = nextActive(current)
        refreshTurnThrows()
    }

    private fun nextActive(from: Int): Int {
        var i = from
        repeat(players.size) {
            i = (i + 1) % players.size
            if (!isFinishedPlayer(i)) return i
        }
        return from
    }

    private fun recordTurn() {
        dartMeta.forEachIndexed { i, meta ->
            if (meta[1] == turnCounter) {
                turnsLog.add(Turn(meta[0], 1, dartContrib[i]))
            }
        }
    }

    private fun onLegWon(idx: Int) {
        legsWonInSet[idx] = legsWonInSet[idx] + 1
        legsWonTotal[idx] = legsWonTotal[idx] + 1
        message = "${players[idx].name} wygrywa lega!"
        if (legWinCondition(legsWonInSet[idx], legsTarget, setLegMode)) {
            setsWon[idx] = setsWon[idx] + 1
            if (setWinCondition(setsWon[idx], setsTarget, setLegMode)) {
                onPlayerFinished(idx)
                return
            }
            message = "${players[idx].name} wygrywa seta!"
            setNumber++
            for (i in players.indices) legsWonInSet[i] = 0
            starterIdx = nextActive(setNumber % players.size - 1)
            startLeg()
        } else {
            starterIdx = nextActive(setNumber % players.size + legsWonInSet.sum() - 1)
            startLeg()
        }
    }

    private fun startLeg() {
        legNumber++
        for (i in players.indices) {
            scores[i] = startScore
            checkedIn[i] = checkIn == CheckInMode.STRAIGHT
            dartsInLeg[i] = 0
            if (i < playerLastTurnThrows.size) playerLastTurnThrows[i] = emptyList()
            if (i < playerLastTurnBusted.size) playerLastTurnBusted[i] = false
        }
        dartsUsed = 0
        current = starterIdx
        turnCounter++
        refreshTurnThrows()
    }

    private fun onPlayerFinished(idx: Int) {
        if (idx in finishedOrder) return
        finishedOrder.add(idx)
        val place = finishedOrder.size
        message = if (place == 1) "${players[idx].name} wygrywa mecz!" else "${players[idx].name} zdobywa $place. miejsce!"
        val remaining = players.indices.filterNot { it in finishedOrder }
        if (remaining.size <= 1) {
            val last = remaining.firstOrNull()
            if (last != null) finishedOrder.add(last)
            finalize()
        } else {
            pendingFinishIdx = idx
        }
    }

    fun continueAfterFinish() {
        pendingFinishIdx = -1
        dartsUsed = 0
        turnThrows.clear()
        current = nextActive(current)
        turnCounter++
        message = "Tura: ${players[current].name}"
    }

    fun finishNow() {
        pendingFinishIdx = -1
        val remaining = players.indices.filter { !finishedOrder.contains(it) }
            .sortedWith(compareByDescending<Int> { setsWon[it] }.thenBy { scores[it] })
        finishedOrder.addAll(remaining)
        finalize()
    }

    private fun finalize() {
        finished = true
        turnThrows.clear()
    }

    fun undo() {
        if (snapshots.isEmpty()) return
        lastThrowBust = false
        lastTurnThreeZeros = false
        val s = snapshots.removeAt(snapshots.size - 1)
        restore(s)
        message = "Cofnięto rzut"
    }

    fun avg(idx: Int): Double {
        val turns = turnsLog.filter { it.playerIdx == idx }
        val darts = turns.sumOf { it.darts }
        return if (darts > 0) turns.sumOf { it.score }.toDouble() / darts * 3 else 0.0
    }

    /** Punkty wykresu: (pozostaly wynik, index lotki) w ostatniej ledze gracza. */
    fun perfChartPoints(idx: Int): List<Pair<Int, Int>> {
        val legsOf = dartMeta.filter { it[0] == idx }.map { it[2] }
        if (legsOf.isEmpty()) return emptyList()
        val leg = legsOf.max()
        val points = mutableListOf<Pair<Int, Int>>()
        var rem = startScore
        dartLog.forEachIndexed { i, _ ->
            val meta = dartMeta[i]
            if (meta[0] == idx && meta[2] == leg) {
                rem -= dartContrib[i]
                points.add(rem to i)
            }
        }
        return points
    }

    fun dartsInLeg(idx: Int): Int = dartsInLeg.getOrElse(idx) { 0 }

    fun perf(idx: Int): Perf {
        val turns = turnsLog.filter { it.playerIdx == idx }
        val p60 = turns.count { it.score in 60..99 }
        val p100 = turns.count { it.score in 100..139 }
        val p140 = turns.count { it.score in 140..179 }
        val p180 = turns.count { it.score == 180 }
        return Perf(
            throws = turns.size,
            points = turns.sumOf { it.score },
            bestTurn = turns.maxOfOrNull { it.score } ?: 0,
            first9 = turns.take(3).sumOf { it.score },
            marks = 0,
            doubles = doubles[idx],
            triples = triples[idx],
            legsWon = legsWonTotal[idx],
            legsPlayed = maxOf(legsWonTotal.sum(), 1),
            maxCheckout = maxCheckout[idx],
            minDartsLeg = if (minDartsLeg[idx] == Int.MAX_VALUE) 0 else minDartsLeg[idx],
            p60 = p60,
            p100 = p100,
            p140 = p140,
            p180 = p180,
            hitsBySector = sectorHits[idx].toMap()
        )
    }

    fun toJson(): JSONObject {
        val o = snapshot()
        o.put("startScore", startScore).put("checkIn", checkIn.name).put("checkOut", checkOut.name)
        o.put("legsTarget", legsTarget).put("setsTarget", setsTarget).put("setLegMode", setLegMode.name)
        o.put("legNumber", legNumber).put("setNumber", setNumber)
        val dl = JSONArray(); dartLog.forEach { dl.put(JSONArray(listOf(it.value, it.mult, it.segment))) }
        val dm = JSONArray(); dartMeta.forEach { dm.put(JSONArray(listOf(it[0], it[1], it[2], it[3]))) }
        val dc = JSONArray(dartContrib)
        o.put("dartLog", dl).put("dartMeta", dm).put("dartContrib", dc)
        val tl = JSONArray(); turnsLog.forEach { tl.put(JSONArray(listOf(it.playerIdx, it.darts, it.score))) }
        o.put("turnsLog", tl)
        val sn = JSONArray(); snapshots.forEach { sn.put(it) }
        o.put("snapshots", sn)
        o.put("bots", JSONObject().also { b -> bots.forEach { (k, v) -> b.put(k, v.name) } })
        return o
    }

    companion object {
        fun fromJson(o: JSONObject, players: List<Player>, bots: Map<String, BotLevel>): X01Engine {
            val b = o.optJSONObject("bots")
            val botsAll = bots.toMutableMap()
            b?.keys()?.forEach { k -> botsAll[k] = BotLevel.valueOf(b.getString(k)) }
            val e = X01Engine(players, botsAll, o.getInt("startScore"),
                CheckInMode.valueOf(o.getString("checkIn")), CheckOutMode.valueOf(o.getString("checkOut")),
                o.getInt("legsTarget"), o.getInt("setsTarget"), SetLegMode.valueOf(o.getString("setLegMode")))
            e.legNumber = o.getInt("legNumber"); e.setNumber = o.getInt("setNumber")
            e.restore(o)
            val dl = o.getJSONArray("dartLog")
            for (i in 0 until dl.length()) {
                val a = dl.getJSONArray(i)
                e.dartLog.add(DartThrow(a.getInt(0), a.getInt(1), a.getInt(2)))
            }
            val dm = o.getJSONArray("dartMeta")
            for (i in 0 until dm.length()) {
                val a = dm.getJSONArray(i)
                e.dartMeta.add(intArrayOf(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3)))
            }
            val dc = o.optJSONArray("dartContrib")
            if (dc != null) for (i in 0 until dc.length()) e.dartContrib.add(dc.getInt(i))
            val tl = o.getJSONArray("turnsLog")
            for (i in 0 until tl.length()) {
                val a = tl.getJSONArray(i)
                e.turnsLog.add(Turn(a.getInt(0), a.getInt(1), a.getInt(2)))
            }
            val sn = o.optJSONArray("snapshots")
            if (sn != null) for (i in 0 until sn.length()) e.snapshots.add(sn.getJSONObject(i))
            e.refreshTurnThrows()
            return e
        }
    }
}

class CricketEngine(
    val players: List<Player>,
    val bots: Map<String, BotLevel>,
    val pointsOn: Boolean = true,
    val cutThroat: Boolean = false,
    val legsTarget: Int = 1,
    val setsTarget: Int = 1,
    val setLegMode: SetLegMode = SetLegMode.FIRST_TO
) {
    val marks = mutableStateListOf<List<Int>>()
    val points = mutableStateListOf<Int>()
    val legsWonInSet = mutableStateListOf<Int>()
    val setsWon = mutableStateListOf<Int>()
    val legsWonTotal = mutableStateListOf<Int>()
    var current by mutableStateOf(0)
    var dartsUsed by mutableStateOf(0)
    var legNumber by mutableStateOf(0)
    var setNumber by mutableStateOf(0)
    var starterIdx by mutableStateOf(0)
    var turnCounter by mutableStateOf(0)
    var message by mutableStateOf("")
    var spoken by mutableStateOf("")
    var finished by mutableStateOf(false)
    var lastTurnThreeZeros by mutableStateOf(false)
    val finishedOrder = mutableStateListOf<Int>()
    var pendingFinishIdx by mutableStateOf(-1)
    val turnThrows = mutableStateListOf<Pair<Int, Int>>()
    val playerLastTurnThrows = mutableStateListOf<List<Pair<Int, Int>>>()

    private val dartLog = mutableListOf<Pair<Int, Int>>() // (sectorIdx, mult)
    private val dartMeta = mutableListOf<IntArray>()
    private val snapshots = mutableListOf<JSONObject>()
    private val dartsCount = mutableListOf<Int>()
    private val marksTotal = mutableListOf<Int>()
    private var turnMarks = 0
    private val doubles = mutableListOf<Int>()
    private val triples = mutableListOf<Int>()
    private val sectorHits = mutableListOf<MutableMap<String, Int>>()

    init {
        players.forEach {
            marks.add(List(7) { 0 }); points.add(0)
            legsWonInSet.add(0); setsWon.add(0); legsWonTotal.add(0)
            dartsCount.add(0); marksTotal.add(0); doubles.add(0); triples.add(0)
            sectorHits.add(mutableMapOf())
            playerLastTurnThrows.add(emptyList())
        }
    }

    fun isBot(idx: Int) = (bots[players[idx].id] ?: BotLevel.NONE) != BotLevel.NONE
    fun isFinishedPlayer(idx: Int) = finishedOrder.contains(idx)
    private fun nextActive(from: Int): Int {
        var i = from
        repeat(players.size) {
            i = (i + 1) % players.size
            if (!isFinishedPlayer(i)) return i
        }
        return from
    }

    private fun snapshot(): JSONObject {
        val o = JSONObject()
        o.put("current", current).put("darts", dartsUsed).put("leg", legNumber).put("set", setNumber)
        o.put("starter", starterIdx).put("turnCounter", turnCounter).put("pending", pendingFinishIdx)
        o.put("finished", finished)
        o.put("points", JSONArray(points))
        val m = JSONArray(); marks.forEach { m.put(JSONArray(it)) }
        o.put("marks", m)
        o.put("legsWonInSet", JSONArray(legsWonInSet)).put("setsWon", JSONArray(setsWon))
        o.put("legsWonTotal", JSONArray(legsWonTotal))
        o.put("finishedOrder", JSONArray(finishedOrder))
        o.put("dartLogSize", dartLog.size)
        o.put("dartsCount", JSONArray(dartsCount)).put("marksTotal", JSONArray(marksTotal))
        o.put("doubles", JSONArray(doubles)).put("triples", JSONArray(triples))
        val sh = JSONArray()
        sectorHits.forEach { m2 ->
            val mo = JSONObject(); m2.forEach { (k, v) -> mo.put(k, v) }
            sh.put(mo)
        }
        o.put("sectorHits", sh)
        o.put("turnMarks", turnMarks)
        val pltt = JSONArray()
        playerLastTurnThrows.forEach { l ->
            val arr = JSONArray()
            l.forEach { (s, m) -> arr.put(JSONObject().put("s", s).put("m", m)) }
            pltt.put(arr)
        }
        o.put("playerLastTurnThrows", pltt)
        return o
    }

    private fun restore(o: JSONObject) {
        current = o.getInt("current"); dartsUsed = o.getInt("darts")
        legNumber = o.getInt("leg"); setNumber = o.getInt("set")
        starterIdx = o.getInt("starter"); turnCounter = o.getInt("turnCounter")
        pendingFinishIdx = o.getInt("pending"); finished = o.getBoolean("finished")
        points.clear(); marks.clear(); legsWonInSet.clear(); setsWon.clear(); legsWonTotal.clear(); finishedOrder.clear()
        dartsCount.clear(); marksTotal.clear(); doubles.clear(); triples.clear(); sectorHits.clear()
        val pa = o.getJSONArray("points"); for (i in 0 until pa.length()) points.add(pa.getInt(i))
        val m = o.getJSONArray("marks")
        for (i in 0 until m.length()) marks.add((0 until 7).map { m.getJSONArray(i).getInt(it) })
        val la = o.getJSONArray("legsWonInSet"); for (i in 0 until la.length()) legsWonInSet.add(la.getInt(i))
        val st = o.getJSONArray("setsWon"); for (i in 0 until st.length()) setsWon.add(st.getInt(i))
        val lt = o.getJSONArray("legsWonTotal"); for (i in 0 until lt.length()) legsWonTotal.add(lt.getInt(i))
        val fo = o.getJSONArray("finishedOrder"); for (i in 0 until fo.length()) finishedOrder.add(fo.getInt(i))
        val da = o.getJSONArray("dartsCount"); for (i in 0 until da.length()) dartsCount.add(da.getInt(i))
        val ma = o.getJSONArray("marksTotal"); for (i in 0 until ma.length()) marksTotal.add(ma.getInt(i))
        val doa = o.getJSONArray("doubles"); for (i in 0 until doa.length()) doubles.add(doa.getInt(i))
        val ta = o.getJSONArray("triples"); for (i in 0 until ta.length()) triples.add(ta.getInt(i))
        val sh = o.getJSONArray("sectorHits")
        for (i in 0 until sh.length()) {
            val mo = sh.getJSONObject(i); val m2 = mutableMapOf<String, Int>()
            mo.keys().forEach { k -> m2[k] = mo.getInt(k) }
            sectorHits.add(m2)
        }
        while (dartLog.size > o.getInt("dartLogSize")) { dartLog.removeAt(dartLog.size - 1); dartMeta.removeAt(dartMeta.size - 1) }
        turnMarks = o.optInt("turnMarks", 0)
        playerLastTurnThrows.clear()
        val pltt = o.optJSONArray("playerLastTurnThrows")
        if (pltt != null) {
            for (i in 0 until pltt.length()) {
                val arr = pltt.getJSONArray(i)
                val l = (0 until arr.length()).map {
                    val item = arr.getJSONObject(it)
                    item.getInt("s") to item.getInt("m")
                }
                playerLastTurnThrows.add(l)
            }
        } else {
            repeat(players.size) { playerLastTurnThrows.add(emptyList()) }
        }
        refreshTurnThrows()
    }

    private fun refreshTurnThrows() {
        turnThrows.clear()
        dartMeta.forEachIndexed { i, m ->
            if (m[0] == current && m[1] == turnCounter) turnThrows.add(dartLog[i])
        }
    }

    fun submitDart(sectorIdx: Int, mult: Int) {
        if (finished || dartsUsed >= 3) return
        spoken = ""
        if (dartsUsed == 0) {
            turnMarks = 0
            lastTurnThreeZeros = false
        }
        snapshots.add(snapshot())
        dartLog.add(sectorIdx to mult)
        dartMeta.add(intArrayOf(current, turnCounter))
        val idx = current
        val marksBefore = marks[idx][sectorIdx]
        val gained = CricketRules.marksAdded(marksBefore, mult)
        val excessMarks = CricketRules.excessMarks(marksBefore, mult)
        marks[idx] = marks[idx].toMutableList().also { it[sectorIdx] += gained }
        marksTotal[idx] += gained
        turnMarks += gained
        dartsCount[idx]++
        when (mult) {
            2 -> doubles[idx] = doubles[idx] + 1
            3 -> triples[idx] = triples[idx] + 1
        }
        val sectorVal = SECTORS[sectorIdx]
        val label = when { mult == 0 -> "0"; mult == 2 -> "D$sectorVal"; mult == 3 -> "T$sectorVal"; else -> sectorVal.toString() }
        if (mult > 0) sectorHits[idx][label] = (sectorHits[idx][label] ?: 0) + 1
        if (pointsOn && excessMarks > 0) {
            val openOpponents = players.indices.filter { j ->
                j != idx && !isFinishedPlayer(j) && marks[j][sectorIdx] < 3
            }
            if (cutThroat) {
                openOpponents.forEach { j -> points[j] = points[j] + sectorVal * excessMarks }
            } else if (openOpponents.isNotEmpty()) {
                points[idx] = points[idx] + sectorVal * excessMarks
            }
        }
        message = "${players[idx].name}: $label"
        dartsUsed++
        turnThrows.add(sectorIdx to mult)

        val spokenThrow = when {
            mult == 0 -> "zero"
            sectorVal == 25 -> when (mult) {
                2 -> "double bull"
                else -> "bull"
            }
            mult == 3 -> "triple $sectorVal"
            mult == 2 -> "double $sectorVal"
            else -> sectorVal.toString()
        }
        spoken = spokenThrow

        CricketRules.winnerIndex(marks, points, pointsOn, cutThroat, finishedOrder)?.let { winner ->
            if (current < playerLastTurnThrows.size) {
                playerLastTurnThrows[current] = turnThrows.toList()
            }
            onLegWon(winner)
            return
        }
        if (dartsUsed >= 3) {
            lastTurnThreeZeros = (turnThrows.size == 3 && turnThrows.all { it.second == 0 })
            endTurn()
        }
    }

    private fun endTurn() {
        val prevIdx = current
        if (prevIdx < playerLastTurnThrows.size) {
            playerLastTurnThrows[prevIdx] = turnThrows.toList()
        }
        dartsUsed = 0
        turnCounter++
        current = nextActive(current)
        turnThrows.clear()
    }

    private fun onLegWon(idx: Int) {
        legsWonInSet[idx] = legsWonInSet[idx] + 1
        legsWonTotal[idx] = legsWonTotal[idx] + 1
        message = "${players[idx].name} wygrywa lega!"
        if (legWinCondition(legsWonInSet[idx], legsTarget, setLegMode)) {
            setsWon[idx] = setsWon[idx] + 1
            if (setWinCondition(setsWon[idx], setsTarget, setLegMode)) {
                onPlayerFinished(idx)
                return
            }
            message = "${players[idx].name} wygrywa seta!"
            setNumber++
            for (i in players.indices) legsWonInSet[i] = 0
            starterIdx = nextActive(setNumber % players.size - 1)
            startLeg()
        } else {
            starterIdx = nextActive(setNumber % players.size + legsWonInSet.sum() - 1)
            startLeg()
        }
    }

    private fun startLeg() {
        legNumber++
        for (i in players.indices) {
            marks[i] = List(7) { 0 }
            points[i] = 0
            if (i < playerLastTurnThrows.size) playerLastTurnThrows[i] = emptyList()
        }
        dartsUsed = 0
        current = starterIdx
        turnCounter++
        turnThrows.clear()
    }

    private fun onPlayerFinished(idx: Int) {
        if (idx in finishedOrder) return
        finishedOrder.add(idx)
        val place = finishedOrder.size
        message = if (place == 1) "${players[idx].name} wygrywa mecz!" else "${players[idx].name} zdobywa $place. miejsce!"
        val remaining = players.indices.filterNot { it in finishedOrder }
        if (remaining.size <= 1) {
            val last = remaining.firstOrNull()
            if (last != null) finishedOrder.add(last)
            finished = true
        } else {
            pendingFinishIdx = idx
        }
    }

    fun continueAfterFinish() {
        pendingFinishIdx = -1
        if (isFinishedPlayer(current) || dartsUsed >= 3) {
            dartsUsed = 0
            current = nextActive(current)
            turnCounter++
        }
        message = "Tura: ${players[current].name}"
    }

    fun finishNow() {
        pendingFinishIdx = -1
        val remaining = players.indices.filter { !finishedOrder.contains(it) }
            .sortedWith(compareByDescending<Int> { setsWon[it] }
                .thenByDescending { legsWonInSet[it] }
                .thenBy { if (cutThroat && pointsOn) points[it] else -points[it] }
                .thenByDescending { marks[it].count { m -> m >= 3 } })
        finishedOrder.addAll(remaining)
        finished = true
    }

    fun undo() {
        if (snapshots.isEmpty()) return
        lastTurnThreeZeros = false
        val s = snapshots.removeAt(snapshots.size - 1)
        restore(s)
        message = "Cofnięto rzut"
    }

    fun dartsCount(idx: Int): Int = dartsCount.getOrElse(idx) { 0 }

    fun mpr(idx: Int): Double {
        return if (dartsCount[idx] > 0) marksTotal[idx].toDouble() / dartsCount[idx] * 3 else 0.0
    }

    fun perf(idx: Int): Perf {
        return Perf(
            throws = dartsCount[idx],
            points = 0,
            bestTurn = 0,
            first9 = 0,
            marks = marksTotal[idx],
            doubles = doubles[idx],
            triples = triples[idx],
            legsWon = legsWonTotal[idx],
            maxCheckout = 0,
            minDartsLeg = 0,
            hitsBySector = sectorHits[idx].toMap()
        )
    }

    fun toJson(): JSONObject {
        val o = snapshot()
        o.put("pointsOn", pointsOn).put("cutThroat", cutThroat)
        o.put("legsTarget", legsTarget).put("setsTarget", setsTarget).put("setLegMode", setLegMode.name)
        o.put("legNumber", legNumber).put("setNumber", setNumber)
        val dl = JSONArray(); dartLog.forEach { dl.put(JSONArray(listOf(it.first, it.second))) }
        val dm = JSONArray(); dartMeta.forEach { dm.put(JSONArray(listOf(it[0], it[1]))) }
        o.put("dartLog", dl).put("dartMeta", dm)
        val sn = JSONArray(); snapshots.forEach { sn.put(it) }
        o.put("snapshots", sn)
        o.put("bots", JSONObject().also { b -> bots.forEach { (k, v) -> b.put(k, v.name) } })
        return o
    }

    companion object {
        val SECTORS = listOf(20, 19, 18, 17, 16, 15, 25)

        fun fromJson(o: JSONObject, players: List<Player>, bots: Map<String, BotLevel>): CricketEngine {
            val b = o.optJSONObject("bots")
            val botsAll = bots.toMutableMap()
            b?.keys()?.forEach { k -> botsAll[k] = BotLevel.valueOf(b.getString(k)) }
            val e = CricketEngine(players, botsAll, o.optBoolean("pointsOn", true), o.optBoolean("cutThroat", false),
                o.getInt("legsTarget"), o.getInt("setsTarget"), SetLegMode.valueOf(o.getString("setLegMode")))
            e.legNumber = o.getInt("legNumber"); e.setNumber = o.getInt("setNumber")
            e.restore(o)
            val dl = o.getJSONArray("dartLog")
            for (i in 0 until dl.length()) {
                val a = dl.getJSONArray(i)
                e.dartLog.add(a.getInt(0) to a.getInt(1))
            }
            val dm = o.getJSONArray("dartMeta")
            for (i in 0 until dm.length()) {
                val a = dm.getJSONArray(i)
                e.dartMeta.add(intArrayOf(a.getInt(0), a.getInt(1)))
            }
            val sn = o.optJSONArray("snapshots")
            if (sn != null) for (i in 0 until sn.length()) e.snapshots.add(sn.getJSONObject(i))
            return e
        }
    }
}

class AroundEngine(
    val players: List<Player>,
    val bots: Map<String, BotLevel>,
    val doublesOnly: Boolean
) {
    val targets = mutableStateListOf<Int>()
    val throwsPerPlayer = mutableStateListOf<Int>()
    var current by mutableStateOf(0)
    var dartsUsed by mutableStateOf(0)
    val turnEvents = mutableStateListOf<JSONObject>()
    var finished by mutableStateOf(false)
    var winnerIdx by mutableStateOf(-1)
    var message by mutableStateOf("")
    var spoken by mutableStateOf("")
    var lastTurnThreeZeros by mutableStateOf(false)
    private var turnHits = 0

    init {
        players.forEach { targets.add(1); throwsPerPlayer.add(0) }
    }

    fun isBot(idx: Int) = (bots[players[idx].id] ?: BotLevel.NONE) != BotLevel.NONE

    fun submit(value: Int, mult: Int) {
        if (finished) return
        spoken = ""
        if (dartsUsed >= 3) advance()
        if (dartsUsed == 0) {
            turnHits = 0
            lastTurnThreeZeros = false
        }
        val idx = current
        turnEvents.add(JSONObject().put("idx", idx).put("darts", dartsUsed)
            .put("target", targets[idx]).put("throws", throwsPerPlayer[idx]))
        val target = targets[idx]
        val hit = when {
            target == 21 -> value == 25 || value == 50
            doublesOnly -> mult == 2 && value == target
            else -> value == target
        }
        if (hit) {
            turnHits++
            if (target == 21) {
                finished = true
                winnerIdx = idx
                message = "${players[idx].name} trafia BULL i wygrywa!"
                spoken = "BULL"
            } else {
                targets[idx] = target + 1
                message = "${players[idx].name}: trafiony $target!"
            }
        } else {
            message = "${players[idx].name}: 0"
        }
        throwsPerPlayer[idx] = throwsPerPlayer[idx] + 1
        dartsUsed++
        if (dartsUsed >= 3) {
            if (spoken.isEmpty()) spoken = turnHits.toString()
            lastTurnThreeZeros = (turnHits == 0)
            advance()
        }
    }

    fun undo() {
        if (finished || turnEvents.isEmpty()) return
        lastTurnThreeZeros = false
        val last = turnEvents.removeAt(turnEvents.size - 1)
        val idx = last.getInt("idx")
        targets[idx] = last.getInt("target")
        throwsPerPlayer[idx] = last.getInt("throws")
        dartsUsed = last.getInt("darts")
        current = idx
        message = "Cofnięto rzut"
    }

    private fun advance() {
        dartsUsed = 0
        current = (current + 1) % players.size
    }

    fun botPlay() {
        if (finished || !isBot(current)) return
        val level = bots[players[current].id] ?: BotLevel.NORMAL
        repeat(3) {
            if (finished) return
            val (hit, mult) = Bot.aroundDart(doublesOnly, level)
            val value = if (hit == 1) { if (targets[current] == 21) 25 else minOf(targets[current], 20) } else 0
            submit(value, mult)
        }
    }

    fun finishNow() {
        finished = true
        if (winnerIdx < 0) {
            val best = players.indices.maxWithOrNull(
                compareBy<Int> { targets.getOrElse(it) { 0 } }
                    .thenByDescending { -throwsPerPlayer.getOrElse(it) { 0 } }
            ) ?: 0
            winnerIdx = best
        }
    }

    fun perf(idx: Int): Perf {
        val cleared = targets[idx] - 1 + if (finished && winnerIdx == idx) 1 else 0
        return Perf(throws = throwsPerPlayer[idx], points = cleared, bestTurn = 0, first9 = 0, marks = 0)
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("doublesOnly", doublesOnly).put("current", current).put("darts", dartsUsed)
        o.put("finished", finished).put("winnerIdx", winnerIdx)
        o.put("targets", JSONArray(targets)).put("throws", JSONArray(throwsPerPlayer))
        o.put("turnEvents", JSONArray(turnEvents))
        return o
    }

    companion object {
        fun fromJson(o: JSONObject, players: List<Player>, bots: Map<String, BotLevel>): AroundEngine {
            val e = AroundEngine(players, bots, o.getBoolean("doublesOnly"))
            e.current = o.getInt("current")
            e.dartsUsed = o.getInt("darts")
            e.finished = o.getBoolean("finished")
            e.winnerIdx = o.getInt("winnerIdx")
            e.targets.clear(); e.throwsPerPlayer.clear(); e.turnEvents.clear()
            val ta = o.getJSONArray("targets")
            for (i in 0 until ta.length()) e.targets.add(ta.getInt(i))
            val tha = o.getJSONArray("throws")
            for (i in 0 until tha.length()) e.throwsPerPlayer.add(tha.getInt(i))
            val te = o.getJSONArray("turnEvents")
            for (i in 0 until te.length()) e.turnEvents.add(te.getJSONObject(i))
            return e
        }
    }
}
