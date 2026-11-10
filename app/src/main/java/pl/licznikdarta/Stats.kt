package pl.licznikdarta

data class PlayerStats(
    val player: String,
    val games: Int,
    val wins: Int,
    val winRate: Double,
    val legsPlayed: Int,
    val legsWon: Int,
    val legsWinRate: Double,
    val avgDartsPerLeg: Double,
    val doublePercent: Double,
    val triplePercent: Double,
    val avg: Double,
    val first9: Double,
    val bestTurn: Int,
    val p60: Int,
    val p100: Int,
    val p140: Int,
    val p180: Int,
    val maxCheckout: Int,
    val minDartsLeg: Int,
    val checkoutPercent: Double,
    val mpr: Double,
    val marks: Int,
    val hits: Map<String, Int>
)

object Stats {
    fun compute(
        games: List<GameRecord>,
        player: String,
        since: Long = 0,
        gameTypeFilter: GameType? = null
    ): PlayerStats {
        val rel = games.filter {
            it.date >= since && player in it.players &&
                (gameTypeFilter == null || it.type == gameTypeFilter)
        }
        val x01 = rel.filter { it.type == GameType.X01 }
        val xp = x01.mapNotNull { it.perPlayer[player] }
        val cricket = rel.filter { it.type == GameType.CRICKET }
        val cp = cricket.mapNotNull { it.perPlayer[player] }
        val allPerfs = rel.mapNotNull { it.perPlayer[player] }

        val totalTurns = xp.sumOf { it.throws }
        val points = xp.sumOf { it.points }
        val avg = if (totalTurns > 0) points.toDouble() / totalTurns * 3 else 0.0
        val withThrows = xp.filter { it.throws > 0 }
        val f9 = if (withThrows.isNotEmpty()) withThrows.sumOf { it.first9 }.toDouble() / (withThrows.size * 3) else 0.0
        val best = xp.maxOfOrNull { it.bestTurn } ?: 0

        val cDarts = cp.sumOf { it.throws }
        val cMarks = cp.sumOf { it.marks }
        val mpr = if (cDarts > 0) cMarks.toDouble() / cDarts * 3 else 0.0

        val wins = rel.count { it.winner == player }
        val gamesCount = rel.size
        val winRate = if (gamesCount > 0) wins.toDouble() / gamesCount * 100 else 0.0

        val legsWon = allPerfs.sumOf { it.legsWon }
        val legsPlayed = allPerfs.sumOf { maxOf(it.legsPlayed, it.legsWon) }
        val legsWinRate = if (legsPlayed > 0) legsWon.toDouble() / legsPlayed * 100 else 0.0

        val totalDartsEstimated = totalTurns * 3 + cDarts
        val totalDoubles = allPerfs.sumOf { it.doubles }
        val totalTriples = allPerfs.sumOf { it.triples }
        val doublePercent = if (totalDartsEstimated > 0) totalDoubles.toDouble() / totalDartsEstimated * 100 else 0.0
        val triplePercent = if (totalDartsEstimated > 0) totalTriples.toDouble() / totalDartsEstimated * 100 else 0.0

        val avgDartsPerLeg = if (legsPlayed > 0) totalDartsEstimated.toDouble() / legsPlayed else 0.0

        val p60 = xp.sumOf { it.p60 }
        val p100 = xp.sumOf { it.p100 }
        val p140 = xp.sumOf { it.p140 }
        val p180 = xp.sumOf { it.p180 }

        val maxCheckout = xp.maxOfOrNull { it.maxCheckout } ?: 0
        val minDartsNonZero = xp.map { it.minDartsLeg }.filter { it > 0 }
        val minDartsLeg = minDartsNonZero.minOrNull() ?: 0

        val checkoutsAttempted = xp.sumOf { it.checkoutAttempts }
        val checkoutPercent = if (checkoutsAttempted > 0) {
            legsWon.toDouble() / checkoutsAttempted * 100
        } else if (legsWon > 0) {
            // estimate if attempts not explicitly tracked
            (legsWon.toDouble() / maxOf(legsPlayed, 1) * 35.0).coerceAtMost(100.0)
        } else 0.0

        val hits = mutableMapOf<String, Int>()
        allPerfs.forEach { p ->
            p.hitsBySector.forEach { (k, v) -> hits[k] = (hits[k] ?: 0) + v }
        }

        return PlayerStats(
            player = player,
            games = gamesCount,
            wins = wins,
            winRate = winRate,
            legsPlayed = legsPlayed,
            legsWon = legsWon,
            legsWinRate = legsWinRate,
            avgDartsPerLeg = avgDartsPerLeg,
            doublePercent = doublePercent,
            triplePercent = triplePercent,
            avg = avg,
            first9 = f9,
            bestTurn = best,
            p60 = p60,
            p100 = p100,
            p140 = p140,
            p180 = p180,
            maxCheckout = maxCheckout,
            minDartsLeg = minDartsLeg,
            checkoutPercent = checkoutPercent,
            mpr = mpr,
            marks = cMarks,
            hits = hits
        )
    }
}
