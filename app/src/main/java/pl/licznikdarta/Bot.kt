package pl.licznikdarta

import kotlin.random.Random

object Bot {

    private fun accSingle(l: BotLevel) = when (l) { BotLevel.EASY -> 0.45; BotLevel.NORMAL -> 0.62; BotLevel.HARD -> 0.78; BotLevel.NONE -> 1.0 }
    private fun accDouble(l: BotLevel) = when (l) { BotLevel.EASY -> 0.25; BotLevel.NORMAL -> 0.42; BotLevel.HARD -> 0.58; BotLevel.NONE -> 1.0 }
    private fun accTriple(l: BotLevel) = when (l) { BotLevel.EASY -> 0.10; BotLevel.NORMAL -> 0.26; BotLevel.HARD -> 0.42; BotLevel.NONE -> 1.0 }
    private fun accCheckout(l: BotLevel) = when (l) { BotLevel.EASY -> 0.15; BotLevel.NORMAL -> 0.35; BotLevel.HARD -> 0.55; BotLevel.NONE -> 1.0 }

    private fun attempt(t: DartThrow, level: BotLevel): DartThrow {
        val acc = when (t.mult) { 3 -> accTriple(level); 2 -> accDouble(level); 1 -> accSingle(level); else -> 1.0 }
        return if (Random.nextDouble() < acc) t else DartThrow(0, 0, 0)
    }

    private fun maxScoreDart(level: BotLevel): DartThrow {
        val r = Random.nextDouble()
        val acc = accTriple(level)
        return attempt(
            when {
                r < acc -> DartThrow(60, 3, 20)
                r < acc + 0.15 -> DartThrow(57, 3, 19)
                r < acc + 0.25 -> DartThrow(54, 3, 18)
                else -> DartThrow(20, 1, 20)
            }, level
        )
    }

    /** Rzut przygotowujacy: zostawia finisha jesli sie da. */
    private fun setupDart(remaining: Int, checkOut: CheckOutMode, level: BotLevel): DartThrow {
        val options = listOf(60, 57, 54, 51, 50, 48, 45, 42, 41, 40, 39, 38, 36, 34, 33, 32, 30, 28, 26, 25, 24, 22, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
        val after = remaining - options[0]
        // preferuj wartosc zostawiajaca finisha (60 najczesciej i tak wygrywa)
        val best = options.firstOrNull { v ->
            v <= remaining && Checkouts.hintText(remaining - v, 2, checkOut, true, CheckInMode.STRAIGHT) != null
        } ?: options.firstOrNull { v -> v <= remaining } ?: 60
        val t = valueToThrow(best)
        return attempt(t, level)
    }

    private fun valueToThrow(v: Int): DartThrow = when {
        v == 50 -> DartThrow(50, 2, 25)
        v == 25 -> DartThrow(25, 1, 25)
        v % 3 == 0 && v / 3 in 1..20 -> DartThrow(v, 3, v / 3)
        v % 2 == 0 && v / 2 in 1..20 -> DartThrow(v, 2, v / 2)
        v in 1..20 -> DartThrow(v, 1, v)
        else -> DartThrow(60, 3, 20)
    }

    fun x01Turn(remaining: Int, checkedIn: Boolean, dartsLeft: Int, checkIn: CheckInMode, checkOut: CheckOutMode, level: BotLevel): List<DartThrow> {
        val out = mutableListOf<DartThrow>()
        var rem = remaining
        var playing = checkedIn

        if (!playing && checkIn != CheckInMode.STRAIGHT) {
            val valid = Checkouts.SECTORS.filter { t ->
                when (checkIn) {
                    CheckInMode.DOUBLE -> t.isDouble
                    CheckInMode.MASTER -> t.isDouble || t.isTriple
                    else -> true
                }
            }.sortedByDescending { it.value }
            for (dart in 0 until dartsLeft) {
                val target = valid.firstOrNull { it.value <= rem } ?: valid.first()
                val t = attempt(target, level)
                out.add(t)
                if (t.value > 0) {
                    playing = true
                    rem -= t.value
                    break
                }
            }
            if (!playing) return out
        }

        // Checkout z rozwiazaniem live
        val combo = Checkouts.solve(rem, 3 - out.size, checkOut, true, CheckInMode.STRAIGHT)
        if (combo != null && Random.nextDouble() < accCheckout(level)) {
            combo.forEach { out.add(attempt(it, level)) }
            return out.take(3)
        }

        while (out.size < 3) {
            val dartsLeftNow = 3 - out.size
            val t = if (dartsLeftNow == 1) {
                // ostatnia lotka: sprobuj zamknac albo zostaw finish
                val finish = Checkouts.solve(rem, 1, checkOut, true, CheckInMode.STRAIGHT)
                if (finish != null) attempt(finish.first(), level) else setupDart(rem, checkOut, level)
            } else {
                setupDart(rem, checkOut, level)
            }
            out.add(t)
            rem -= t.value
        }
        return out.take(3)
    }

    /** Cricket: lista (indeksSektora 0..6, mnoznik). */
    fun cricketTurn(myMarks: List<Int>, oppMarks: List<List<Int>>, level: BotLevel): List<Pair<Int, Int>> {
        val sectors = listOf(20, 19, 18, 17, 16, 15, 25)
        val needClose = sectors.indices.filter { myMarks[it] < 3 }
        val out = mutableListOf<Pair<Int, Int>>()
        val wantPoints = needClose.isEmpty()
        for (dart in 0 until 3) {
            val idx: Int = if (!wantPoints) needClose.firstOrNull { myMarks[it] < 3 } ?: needClose.random()
            else {
                val open = sectors.indices.filter { i -> oppMarks.any { it[i] < 3 } }
                if (open.isEmpty()) needClose.random() else open.maxByOrNull { sectors[it] } ?: 0
            }
            val r = Random.nextDouble()
            val mult = if (r < accTriple(level)) 3 else if (r < accTriple(level) + accDouble(level)) 2
            else if (r < accTriple(level) + accDouble(level) + accSingle(level)) 1 else 0
            out.add(idx to mult)
        }
        return out
    }

    fun aroundDart(doublesOnly: Boolean, level: BotLevel): Pair<Int, Int> {
        val r = Random.nextDouble()
        val acc = if (doublesOnly) accDouble(level) else accSingle(level) + accDouble(level) * 0.5
        val mult = if (doublesOnly) 2 else if (r < accSingle(level)) 1 else 0
        return if (r < acc) Pair(1, mult) else Pair(0, mult)
    }
}
