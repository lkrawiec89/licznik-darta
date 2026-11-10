package pl.licznikdarta

/** 62 mozliwe rzuty (jak SECTOR_NAMES w oryginale): 0..20, D1..D25, T1..T20. */
object Checkouts {
    val SECTORS: List<DartThrow> = run {
        val list = mutableListOf<DartThrow>()
        list.add(DartThrow(0, 0, 0))
        for (s in 1..20) list.add(DartThrow(s, 1, s))
        list.add(DartThrow(25, 1, 25))
        for (s in 1..20) list.add(DartThrow(2 * s, 2, s))
        list.add(DartThrow(50, 2, 25))
        for (s in 1..20) list.add(DartThrow(3 * s, 3, s))
        list
    }

    private fun passesCheckIn(t: DartThrow, mode: CheckInMode): Boolean = when (mode) {
        CheckInMode.STRAIGHT -> true
        CheckInMode.DOUBLE -> t.isDouble
        CheckInMode.MASTER -> t.isDouble || t.isTriple
    }

    private fun passesCheckOut(t: DartThrow, mode: CheckOutMode): Boolean = when (mode) {
        CheckOutMode.STRAIGHT -> true
        CheckOutMode.DOUBLE -> t.isDouble
        CheckOutMode.MASTER -> t.isDouble || t.isTriple
    }

    /** Rozwiazanie checkouta dla pozostalego wyniku i liczby lotek. Zwraca liste rzutow lub null. */
    fun solve(remaining: Int, dartsLeft: Int, checkOut: CheckOutMode, checkedIn: Boolean, checkIn: CheckInMode): List<DartThrow>? {
        if (remaining <= 0 || dartsLeft <= 0) return null
        // Jesli gramy Double Out i gracz wszedl do gry (Checked In), preferujemy oficjalna tabele PDC checkoutow
        if (checkOut == CheckOutMode.DOUBLE && checkedIn) {
            val official = throwsFor(remaining)
            if (official.isNotEmpty() && official.size <= dartsLeft) {
                return official
            }
        }
        val maxDarts = minOf(dartsLeft, 3)
        for (n in 1..maxDarts) {
            val combo = tryCombo(remaining, n, checkOut, checkedIn, checkIn)
            if (combo != null) return combo
        }
        return null
    }

    private fun tryCombo(remaining: Int, n: Int, checkOut: CheckOutMode, checkedIn: Boolean, checkIn: CheckInMode): List<DartThrow>? {
        fun rec(cur: List<DartThrow>, sum: Int, depth: Int): List<DartThrow>? {
            if (depth == n) {
                if (sum != remaining) return null
                val lastT = cur.last()
                if (!passesCheckOut(lastT, checkOut)) return null
                if (!checkedIn && !passesCheckIn(cur.first(), checkIn)) return null
                return cur
            }
            val order = candidateOrder(remaining - sum, n - depth, checkOut, checkedIn, checkIn)
            for (t in order) {
                val res = rec(cur + t, sum + t.value, depth + 1)
                if (res != null) return res
            }
            return null
        }
        return rec(emptyList(), 0, 0)
    }

    /** Kolejnosc kandydatow: najpierw rzuty ktore moga byc ostatnie (checkout), potem T20 itd. */
    private fun candidateOrder(target: Int, dartsLeft: Int, checkOut: CheckOutMode, checkedIn: Boolean, checkIn: CheckInMode): List<DartThrow> {
        val order = mutableListOf<DartThrow>()
        val validLast = SECTORS.filter { passesCheckOut(it, checkOut) }
        // ostatnia lotka -> tylko rzuty spelniajace checkout
        val pool = if (dartsLeft == 1) validLast else SECTORS
        // heurystyka: najwyzsze wartosci najpierw, z preferencja T20/D20 przy ustawianiu
        pool.sortedWith(compareByDescending<DartThrow> { it.value }
            .thenByDescending { if (it.isTriple) 1 else 0 }
            .thenByDescending { if (it.isDouble) 1 else 0 })
            .forEach { t ->
                if (dartsLeft > 1 && t.value > target && t.value != 0) return@forEach
                if (!checkedIn && !passesCheckIn(t, checkIn)) return@forEach
                order.add(t)
            }
        return order
    }

    /** Tekst podpowiedzi, np. "T20 → T20 → BULL". */
    fun hintText(remaining: Int, dartsLeft: Int, checkOut: CheckOutMode, checkedIn: Boolean, checkIn: CheckInMode): String? {
        val combo = solve(remaining, dartsLeft, checkOut, checkedIn, checkIn) ?: return null
        return combo.joinToString(" → ") { it.label() }
    }

    /** Standardowa tabela 2..170 (skrot, uzywana gdy lotek=3, double out, checked in). */
    private val table = mapOf(
        2 to "D1", 3 to "1 D1", 4 to "D2", 5 to "1 D2", 6 to "D3", 7 to "3 D2",
        8 to "D4", 9 to "1 D4", 10 to "D5", 11 to "3 D4", 12 to "D6", 13 to "5 D4",
        14 to "D7", 15 to "7 D4", 16 to "D8", 17 to "1 D8", 18 to "D9", 19 to "3 D8",
        20 to "D10", 21 to "5 D8", 22 to "D11", 23 to "7 D8", 24 to "D12", 25 to "9 D8",
        26 to "D13", 27 to "11 D8", 28 to "D14", 29 to "13 D8", 30 to "D15", 31 to "15 D8",
        32 to "D16", 33 to "1 D16", 34 to "D17", 35 to "3 D16", 36 to "D18", 37 to "5 D16",
        38 to "D19", 39 to "7 D16", 40 to "D20", 41 to "9 D16", 42 to "10 D16", 43 to "11 D16",
        44 to "12 D16", 45 to "13 D16", 46 to "14 D16", 47 to "15 D16", 48 to "16 D16",
        49 to "17 D16", 50 to "18 D16", 51 to "19 D16", 52 to "20 D16", 53 to "13 D20",
        54 to "14 D20", 55 to "15 D20", 56 to "16 D20", 57 to "17 D20", 58 to "18 D20",
        59 to "19 D20", 60 to "20 D20", 61 to "T15 D8", 62 to "T10 D16", 63 to "T13 D12",
        64 to "T16 D8", 65 to "T19 D4", 66 to "T10 D18", 67 to "T17 D8", 68 to "T20 D4",
        69 to "T19 D6", 70 to "T10 D20", 71 to "T13 D16", 72 to "T16 D12", 73 to "T19 D8",
        74 to "T14 D16", 75 to "T17 D12", 76 to "T20 D8", 77 to "T19 D10", 78 to "T18 D12",
        79 to "T13 D20", 80 to "T20 D10", 81 to "T19 D12", 82 to "T14 D20", 83 to "T17 D16",
        84 to "T20 D12", 85 to "T15 D20", 86 to "T18 D16", 87 to "T17 D18", 88 to "T16 D20",
        89 to "T19 D16", 90 to "T18 D18", 91 to "T17 D20", 92 to "T20 D16", 93 to "T19 D18",
        94 to "T18 D20", 95 to "T19 D19", 96 to "T20 D18", 97 to "T19 D20", 98 to "T20 D19",
        99 to "T19 10 D16", 100 to "T20 D20", 101 to "T17 BULL", 102 to "T20 10 D16",
        103 to "T20 11 D16", 104 to "T18 BULL", 105 to "T20 13 D16", 106 to "T20 14 D16",
        107 to "T19 18 D16", 108 to "T20 16 D16", 109 to "T20 17 D16", 110 to "T20 BULL",
        111 to "T20 19 D16", 112 to "T20 20 D16", 113 to "T19 16 D20", 114 to "T20 14 D20",
        115 to "T20 15 D20", 116 to "T20 16 D20", 117 to "T20 17 D20", 118 to "T20 18 D20",
        119 to "T20 19 D20", 120 to "T20 20 D20",
        121 to "T20 T15 D8", 122 to "T20 T10 D16", 123 to "T19 T10 D18", 124 to "T20 T16 D8",
        125 to "25 T20 D20", 126 to "T20 T10 D18", 127 to "T20 T17 D8", 128 to "T18 T14 D16",
        129 to "T19 T16 D12", 130 to "T20 T10 D20", 131 to "T20 T13 D16", 132 to "T20 T16 D12",
        133 to "T20 T19 D8", 134 to "T20 T14 D16", 135 to "T20 T17 D12", 136 to "T20 T20 D8",
        137 to "T19 T20 D10", 138 to "T20 T18 D12", 139 to "T19 T14 D20", 140 to "T20 T20 D10",
        141 to "T20 T19 D12", 142 to "T20 T14 D20", 143 to "T20 T17 D16", 144 to "T20 T20 D12",
        145 to "T20 T15 D20", 146 to "T20 T18 D16", 147 to "T20 T17 D18", 148 to "T20 T16 D20",
        149 to "T20 T19 D16", 150 to "T20 T18 D18", 151 to "T20 T17 D20", 152 to "T20 T20 D16",
        153 to "T20 T19 D18", 154 to "T20 T18 D20", 155 to "T20 T19 D19", 156 to "T20 T20 D18",
        157 to "T20 T19 D20", 158 to "T20 T20 D19",
        160 to "T20 T20 D20", 161 to "T20 T17 BULL", 164 to "T20 T18 BULL",
        167 to "T20 T19 BULL", 170 to "T20 T20 BULL"
    )

    fun checkout(remaining: Int): String? = table[remaining]

    fun throwsFor(remaining: Int): List<DartThrow> {
        val s = table[remaining] ?: return emptyList()
        return s.split(" ").map { tok ->
            when {
                tok.startsWith("T") -> DartThrow(3 * tok.removePrefix("T").toInt(), 3, tok.removePrefix("T").toInt())
                tok.startsWith("D") -> DartThrow(2 * tok.removePrefix("D").toInt(), 2, tok.removePrefix("D").toInt())
                tok == "BULL" -> DartThrow(50, 2, 25)
                else -> DartThrow(tok.toInt(), 1, tok.toInt())
            }
        }
    }

    fun hint(remaining: Int, checkOut: CheckOutMode): String {
        if (checkOut == CheckOutMode.DOUBLE) {
            return checkout(remaining)?.let { "Checkout: $it" }
                ?: if (remaining in 42..170) "Brak finisha — celuj w T20"
                else "Celuj w T20"
        }
        return if (remaining <= 170) "Celuj w T20" else "Celuj w T20"
    }
}
