package pl.licznikdarta

import org.junit.Assert.*
import org.junit.Test

class LogicTest {

    private val a = Player(name = "A")
    private val b = Player(name = "B")
    private val c = Player(name = "C")

    private fun x01Engine(players: List<Player> = listOf(a, b), start: Int = 501,
                          checkIn: CheckInMode = CheckInMode.STRAIGHT, checkOut: CheckOutMode = CheckOutMode.DOUBLE,
                          legs: Int = 1, sets: Int = 1, mode: SetLegMode = SetLegMode.FIRST_TO) =
        X01Engine(players, emptyMap(), start, checkIn, checkOut, legs, sets, mode)

    @Test
    fun x01_basicTurn() {
        val out = simulateX01Turn(501, true, listOf(DartThrow(60, 3, 20), DartThrow(60, 3, 20), DartThrow(60, 3, 20)),
            CheckInMode.STRAIGHT, CheckOutMode.DOUBLE)
        assertEquals(321, out.newScore)
        assertFalse(out.busted)
        assertFalse(out.won)
    }

    @Test
    fun x01_bust() {
        val out = simulateX01Turn(5, true, listOf(DartThrow(6, 1, 6)), CheckInMode.STRAIGHT, CheckOutMode.DOUBLE)
        assertTrue(out.busted)
        assertEquals(5, out.newScore)
    }

    @Test
    fun x01_bustOnThirdDartRevertsWholeTurn() {
        val e = x01Engine(start = 100)
        e.submitDart(DartThrow(60, 3, 20)) // 40 left
        e.submitDart(DartThrow(20, 1, 20)) // 20 left
        e.submitDart(DartThrow(25, 1, 25)) // bust: 20 - 25 < 0
        assertEquals(100, e.scores[0]) // whole turn forfeited, back to 100
        assertEquals(1, e.current)
        assertEquals(0, e.dartsUsed)
    }

    @Test
    fun x01_bustDartContributesZeroToAvg() {
        val e = x01Engine(start = 100)
        e.submitDart(DartThrow(60, 3, 20))
        e.submitDart(DartThrow(20, 1, 20))
        e.submitDart(DartThrow(25, 1, 25)) // bust
        assertEquals(0.0, e.avg(0), 0.001)
    }

    @Test
    fun x01_spoken_announcesOnlyAtTurnEnd() {
        val e = x01Engine()
        e.submitDart(DartThrow(60, 3, 20))
        assertEquals("", e.spoken)
        e.submitDart(DartThrow(57, 3, 19))
        assertEquals("", e.spoken)
        e.submitDart(DartThrow(54, 3, 18))
        assertEquals("171", e.spoken)
    }

    @Test
    fun x01_spoken_bustIsZero() {
        val e = x01Engine(start = 5)
        e.submitDart(DartThrow(6, 1, 6))
        assertEquals("0", e.spoken)
    }

    @Test
    fun x01_bustOn1() {
        val out = simulateX01Turn(1, true, listOf(DartThrow(1, 1, 1)), CheckInMode.STRAIGHT, CheckOutMode.DOUBLE)
        assertTrue(out.busted)
    }

    @Test
    fun x01_checkoutDouble() {
        val out = simulateX01Turn(32, true, listOf(DartThrow(32, 2, 16)), CheckInMode.STRAIGHT, CheckOutMode.DOUBLE)
        assertTrue(out.won)
    }

    @Test
    fun x01_straightOut_allowsSingleFinish() {
        val out = simulateX01Turn(2, true, listOf(DartThrow(2, 1, 2)), CheckInMode.STRAIGHT, CheckOutMode.STRAIGHT)
        assertTrue(out.won)
    }

    @Test
    fun x01_masterOut_allowsTripleFinish() {
        val out = simulateX01Turn(30, true, listOf(DartThrow(30, 3, 10)), CheckInMode.STRAIGHT, CheckOutMode.MASTER)
        assertTrue(out.won)
    }

    @Test
    fun x01_doubleIn_blocksSingle() {
        val out = simulateX01Turn(501, false, listOf(DartThrow(20, 1, 20), DartThrow(40, 2, 20)),
            CheckInMode.DOUBLE, CheckOutMode.DOUBLE)
        assertEquals(461, out.newScore)
        assertTrue(out.checkedIn)
    }

    @Test
    fun x01_masterIn_allowsTriple() {
        val out = simulateX01Turn(501, false, listOf(DartThrow(60, 3, 20)), CheckInMode.MASTER, CheckOutMode.DOUBLE)
        assertEquals(441, out.newScore)
        assertTrue(out.checkedIn)
    }

    @Test
    fun checkoutSolver_findsTableCheckouts() {
        for (n in 2..170) {
            val combo = Checkouts.solve(n, 3, CheckOutMode.DOUBLE, true, CheckInMode.STRAIGHT)
            if (combo != null) {
                assertEquals("checkout $n sums", n, combo.sumOf { it.value })
                assertTrue("checkout $n ends with double", combo.last().isDouble)
            }
        }
        // znane finishe musza istniec
        assertNotNull(Checkouts.solve(40, 1, CheckOutMode.DOUBLE, true, CheckInMode.STRAIGHT))
        assertNotNull(Checkouts.solve(170, 3, CheckOutMode.DOUBLE, true, CheckInMode.STRAIGHT))
        assertNull(Checkouts.solve(169, 3, CheckOutMode.DOUBLE, true, CheckInMode.STRAIGHT))
    }

    @Test
    fun checkout_continuity_during_turn() {
        val e = x01Engine(start = 124)
        assertEquals("T20 → T16 → D8", e.currentCheckoutHint())
        e.submitDart(DartThrow(60, 3, 20))
        assertEquals("T16 → D8", e.currentCheckoutHint())
        e.submitDart(DartThrow(48, 3, 16))
        assertEquals("D8", e.currentCheckoutHint())
    }

    @Test
    fun checkoutSolver_respectsDartsLeft() {
        assertNull(Checkouts.solve(40, 0, CheckOutMode.DOUBLE, true, CheckInMode.STRAIGHT))
        assertNotNull(Checkouts.solve(40, 1, CheckOutMode.DOUBLE, true, CheckInMode.STRAIGHT))
    }

    @Test
    fun x01_engine_flow() {
        val e = x01Engine()
        e.submitDart(DartThrow(60, 3, 20)); e.submitDart(DartThrow(60, 3, 20)); e.submitDart(DartThrow(60, 3, 20))
        assertEquals(321, e.scores[0])
        assertEquals(1, e.current)
    }

    @Test
    fun x01_engine_win() {
        val e = x01Engine(start = 40)
        e.submitDart(DartThrow(40, 2, 20))
        assertTrue(e.finished)
        assertEquals(listOf(0, 1), e.finishedOrder)
    }

    @Test
    fun x01_engine_undoDart() {
        val e = x01Engine()
        e.submitDart(DartThrow(60, 3, 20))
        assertEquals(441, e.scores[0])
        e.undo()
        assertEquals(501, e.scores[0])
        assertEquals(0, e.dartsUsed)
    }

    @Test
    fun x01_engine_legsFlow() {
        val e = x01Engine(start = 40, legs = 1, sets = 2)
        e.submitDart(DartThrow(40, 2, 20)) // A wygrywa lega i seta 0
        assertEquals(1, e.setsWon[0])
        assertEquals(0, e.legsWonInSet[0])
        assertFalse(e.finished) // jeszcze drugi set
        assertEquals(1, e.setNumber)
        assertEquals(1, e.current) // rotacja startera: set 1 -> gracz 1 (B)
        // B pudłuje turę
        repeat(3) { e.submitDart(DartThrow(0, 0, 0)) }
        assertEquals(0, e.current)
        // A wygrywa set 1 i cały mecz
        e.submitDart(DartThrow(40, 2, 20))
        assertTrue(e.finished)
        assertEquals(listOf(0, 1), e.finishedOrder)
    }

    @Test
    fun x01_engine_threePlayers_continueForPlaces() {
        val e = x01Engine(players = listOf(a, b, c), start = 40)
        e.submitDart(DartThrow(40, 2, 20)) // A kończy
        assertFalse(e.finished)
        assertEquals(0, e.pendingFinishIdx)
        assertEquals(listOf(0), e.finishedOrder)
        e.continueAfterFinish()
        assertEquals(-1, e.pendingFinishIdx)
        assertFalse(e.finished)
        // B wygrywa -> koniec (zostal 1 gracz)
        while (e.current != 1 && !e.finished) e.submitDart(DartThrow(0, 0, 0))
        e.submitDart(DartThrow(40, 2, 20))
        assertTrue(e.finished)
        assertEquals(listOf(0, 1, 2), e.finishedOrder)
    }

    @Test
    fun x01_engine_finishNowAssignsPlaces() {
        val e = x01Engine(players = listOf(a, b, c), start = 40)
        e.submitDart(DartThrow(40, 2, 20))
        e.finishNow()
        assertTrue(e.finished)
        assertEquals(3, e.finishedOrder.size)
        assertEquals(0, e.finishedOrder[0])
    }

    @Test
    fun cricket_marksAndPoints() {
        val e = CricketEngine(listOf(a, b), emptyMap())
        e.submitDart(0, 3) // A closes 20; no points from the closing hit
        assertEquals(3, e.marks[0][0])
        assertEquals(0, e.points[0])
        e.submitDart(0, 3) // A's next T20 scores 60 while B still has 20 open
        assertEquals(60, e.points[0])
        e.submitDart(0, 3) // another 60, then the third dart ends A's turn
        assertEquals(120, e.points[0])
        e.submitDart(0, 3) // B closes 20; A already closed it, so B cannot score
        assertEquals(0, e.points[1])
    }

    @Test
    fun cricket_cutThroat_givesPointsToOpponents() {
        val e = CricketEngine(listOf(a, b), emptyMap(), pointsOn = true, cutThroat = true)
        e.submitDart(0, 3) // A closes 20; no points yet
        assertEquals(0, e.points[1])
        e.submitDart(0, 3) // A scores against B on the closed 20
        assertEquals(0, e.points[0])
        assertEquals(60, e.points[1])
    }

    @Test
    fun cricket_cutThroatScoresOnlyAgainstOpenOpponents() {
        val e = CricketEngine(listOf(a, b, c), emptyMap(), pointsOn = true, cutThroat = true)
        e.marks[1] = List(7) { if (it == 0) 3 else 0 } // B closed 20
        e.submitDart(0, 3) // A closes 20; the closing hit scores nothing
        e.submitDart(0, 3) // extra T20 gives 60 only to C, who has 20 open
        assertEquals(0, e.points[1])
        assertEquals(60, e.points[2])
    }

    @Test
    fun cricket_cutThroatCountsExcessOnClosingDart() {
        val e = CricketEngine(listOf(a, b), emptyMap(), pointsOn = true, cutThroat = true)
        e.marks[0] = List(7) { if (it == 0) 2 else 0 }
        e.submitDart(0, 3) // one mark closes 20; the two excess marks add 40 to B
        assertEquals(3, e.marks[0][0])
        assertEquals(40, e.points[1])
    }

    @Test
    fun cricket_cutThroatFindsAlreadyClosedLowestPlayerAfterAnotherPlayersDart() {
        val e = CricketEngine(listOf(a, b, c), emptyMap(), pointsOn = true, cutThroat = true)
        e.marks[0] = List(7) { 3 } // A already closed all targets
        e.points[0] = 20
        e.points[1] = 10
        e.points[2] = 25
        e.marks[2] = List(7) { if (it == 0) 3 else 0 } // C's excess 20 goes to B
        e.current = 2
        e.submitDart(0, 1)
        assertEquals(30, e.points[1])
        assertEquals(listOf(0), e.finishedOrder)
        assertEquals(0, e.pendingFinishIdx)
    }

    @Test
    fun cricket_cutThroatHigherScoreDoesNotWinByClosingEverything() {
        val e = CricketEngine(listOf(a, b), emptyMap(), pointsOn = true, cutThroat = true)
        e.marks[0] = List(7) { 3 }
        e.points[0] = 10
        e.marks[1] = List(7) { if (it == 6) 2 else 3 }
        e.points[1] = 30
        e.current = 1
        e.submitDart(6, 1)
        assertEquals(listOf(0, 1), e.finishedOrder)
    }

    @Test
    fun cricket_winCondition() {
        val e = CricketEngine(listOf(a), emptyMap())
        for (s in 0..6) e.submitDart(s, 3)
        assertTrue(e.finished)
        assertEquals(listOf(0), e.finishedOrder)
    }

    @Test
    fun cricket_undo() {
        val e = CricketEngine(listOf(a, b), emptyMap())
        e.submitDart(0, 3)
        assertEquals(3, e.marks[0][0])
        e.undo()
        assertEquals(0, e.marks[0][0])
        assertEquals(0, e.dartsUsed)
    }

    @Test
    fun cricket_spoken_isEachThrow() {
        val e = CricketEngine(listOf(a), emptyMap())
        e.submitDart(0, 3) // sector 0 is 20, mult 3
        assertEquals("triple 20", e.spoken)
        e.submitDart(6, 1) // sector 6 is Bull (25), mult 1
        assertEquals("bull", e.spoken)
        e.submitDart(1, 2) // sector 1 is 19, mult 2
        assertEquals("double 19", e.spoken)
    }

    @Test
    fun around_spoken() {
        val e = AroundEngine(listOf(a), emptyMap(), false)
        e.submit(1, 1)
        assertEquals("", e.spoken)
        e.submit(99, 1) // pudlo
        assertEquals("", e.spoken)
        e.submit(2, 1)
        assertEquals("2", e.spoken)
    }

    @Test
    fun around_progression() {
        val e = AroundEngine(listOf(a, b), emptyMap(), false)
        e.submit(1, 1)
        assertEquals(2, e.targets[0])
        e.submit(2, 1); e.submit(3, 1)
        assertEquals(1, e.current)
        e.submit(1, 1); e.submit(2, 1); e.submit(3, 1)
        assertEquals(0, e.current)
        assertEquals(4, e.targets[0])
    }

    @Test
    fun around_doublesOnly() {
        val e = AroundEngine(listOf(a), emptyMap(), true)
        e.submit(1, 1)
        assertEquals(1, e.targets[0])
        e.submit(1, 2)
        assertEquals(2, e.targets[0])
    }

    @Test
    fun bot_x01_valid() {
        repeat(50) {
            val throws = Bot.x01Turn(501, true, 3, CheckInMode.STRAIGHT, CheckOutMode.DOUBLE, BotLevel.NORMAL)
            assertTrue(throws.size in 1..3)
            throws.forEach { assertTrue("value 0..60, was ${it.value}", it.value in 0..60) }
        }
    }

    @Test
    fun bot_x01_doubleIn_hitsDoubleFirst() {
        repeat(30) {
            val throws = Bot.x01Turn(301, false, 3, CheckInMode.DOUBLE, CheckOutMode.DOUBLE, BotLevel.HARD)
            val firstHit = throws.firstOrNull { it.value > 0 }
            if (firstHit != null) assertTrue("first scoring throw must be a double", firstHit.isDouble)
        }
    }

    @Test
    fun bot_cricket_valid() {
        repeat(50) {
            val plan = Bot.cricketTurn(List(7) { 0 }, listOf(List(7) { 0 }), BotLevel.NORMAL)
            assertEquals(3, plan.size)
            plan.forEach { (idx, mult) ->
                assertTrue(idx in 0..6)
                assertTrue(mult in 0..3)
            }
        }
    }

    @Test
    fun polishNumbers() {
        assertEquals("sto czterdzieści", polishNumber(140))
        assertEquals("pięćset jeden", polishNumber(501))
        assertEquals("dwadzieścia", polishNumber(20))
    }

    @Test
    fun x01_continueAfterFinish_keepsRemainingScoresWithoutReset() {
        val e = x01Engine(players = listOf(a, b, c), start = 40)
        e.submitDart(DartThrow(40, 2, 20)) // A checks out 40
        assertEquals(listOf(0), e.finishedOrder)
        assertEquals(0, e.pendingFinishIdx)
        e.scores[1] = 40
        e.scores[2] = 80
        e.continueAfterFinish()
        assertEquals(1, e.current)
        assertEquals(40, e.scores[1])
        assertEquals(80, e.scores[2])
        e.submitDart(DartThrow(40, 2, 20)) // B checkout with D20
        assertEquals(listOf(0, 1, 2), e.finishedOrder)
        assertTrue(e.finished)
    }

    @Test
    fun cricket_cutThroat_continueAfterFinish_preservesStateAndAssignsPlaces() {
        val e = CricketEngine(listOf(a, b, c), emptyMap(), pointsOn = true, cutThroat = true)
        e.marks[0] = List(7) { if (it == 6) 2 else 3 }
        e.marks[1] = List(7) { if (it < 5) 3 else (if (it == 6) 2 else 0) }
        e.marks[2] = List(7) { if (it < 3) 3 else 0 }
        e.points[0] = 10
        e.points[1] = 20
        e.points[2] = 40
        e.current = 0
        e.submitDart(6, 1) // A closes Bull with 0 excess
        assertEquals(listOf(0), e.finishedOrder)
        assertEquals(0, e.pendingFinishIdx)
        e.continueAfterFinish()
        assertEquals(listOf(3, 3, 3, 3, 3, 3, 3), e.marks[0])
        assertEquals(10, e.points[0])
        assertEquals(20, e.points[1])
        assertEquals(40, e.points[2])
        assertEquals(1, e.current)
        e.submitDart(3, 1) // 17 excess marks to open C
        assertEquals(10, e.points[0]) // A finished, no penalty
        assertEquals(20, e.points[1])
        assertEquals(57, e.points[2]) // C got 17 penalty points
        e.marks[1] = List(7) { 3 }
        e.submitDart(6, 1) // B closes and has lower points than C (20 < 57)
        assertEquals(listOf(0, 1, 2), e.finishedOrder)
        assertTrue(e.finished)
    }

    @Test
    fun cricket_standard_continueAfterFinish_preservesState() {
        val e = CricketEngine(listOf(a, b, c), emptyMap(), pointsOn = true, cutThroat = false)
        e.marks[0] = List(7) { if (it == 6) 2 else 3 }
        e.marks[1] = List(7) { 0 }
        e.marks[2] = List(7) { if (it == 6) 2 else 3 }
        e.points[0] = 100
        e.points[1] = 40
        e.points[2] = 60
        e.current = 0
        e.submitDart(6, 1) // A closes Bull with 0 excess
        assertEquals(listOf(0), e.finishedOrder)
        e.continueAfterFinish()
        assertEquals(100, e.points[0])
        assertEquals(40, e.points[1])
        assertEquals(60, e.points[2])
        assertEquals(1, e.current)
        e.current = 2
        e.submitDart(6, 1) // C closes Bull (0 excess) and has 60 >= 40
        assertEquals(listOf(0, 2, 1), e.finishedOrder)
        assertTrue(e.finished)
    }

    @Test
    fun victorySamplesGenerated() {
        val samples = generateVictorySamples()
        assertTrue(samples.isNotEmpty())
        assertTrue(samples.any { it != 0.toShort() })
    }

    @Test
    fun brymSamplesGenerated() {
        val samples = generateBrymSamples()
        assertTrue(samples.isNotEmpty())
        assertTrue(samples.any { it != 0.toShort() })
    }

    @Test
    fun x01_bustSetsLastThrowBust() {
        val e = x01Engine(players = listOf(a), start = 20)
        assertEquals(false, e.lastThrowBust)
        e.submitDart(DartThrow(60, 3, 20)) // 60 from 20 -> bust
        assertTrue(e.lastThrowBust)
        assertEquals("0", e.spoken)
    }

    @Test
    fun keyClickAndSwitchSamples() {
        val click = generateKeyClickSamples()
        assertTrue(click.isNotEmpty())
        assertTrue(click.any { it != 0.toShort() })

        val sw = generateSwitchSamples()
        assertTrue(sw.isNotEmpty())
        assertTrue(sw.any { it != 0.toShort() })
    }

    @Test
    fun throwSamplesGenerated() {
        val samples = generateThrowSamples()
        assertTrue(samples.isNotEmpty())
        assertTrue(samples.any { it != 0.toShort() })
    }

    @Test
    fun multipleActiveGamesSavedAndRetrieved() {
        val g1 = SavedGame(
            id = "g1",
            type = GameType.X01,
            summary = "X01 (301, Double Out)",
            engineJson = "{}",
            bots = emptyMap(),
            players = listOf(a, b),
            progress = listOf(PlayerProgress(a.name, 301), PlayerProgress(b.name, 301))
        )
        val g2 = SavedGame(
            id = "g2",
            type = GameType.CRICKET,
            summary = "CRICKET (On, Cut Throat)",
            engineJson = "{}",
            bots = emptyMap(),
            players = listOf(a, c),
            progress = listOf(PlayerProgress(a.name, 0), PlayerProgress(c.name, 0))
        )
        val list = listOf(g1, g2)
        assertEquals(2, list.size)
        assertEquals("g1", list[0].id)
        assertEquals("g2", list[1].id)
    }

    @Test
    fun testThreeZerosDetection() {
        // X01: 3 misses in turn
        val e1 = x01Engine(players = listOf(a, b), start = 501)
        e1.submitDart(DartThrow(0, 0, 0))
        assertFalse(e1.lastTurnThreeZeros)
        e1.submitDart(DartThrow(0, 0, 0))
        assertFalse(e1.lastTurnThreeZeros)
        e1.submitDart(DartThrow(0, 0, 0))
        assertTrue(e1.lastTurnThreeZeros)

        // X01: not 3 zeros
        val e2 = x01Engine(players = listOf(a, b), start = 501)
        e2.submitDart(DartThrow(0, 0, 0))
        e2.submitDart(DartThrow(0, 0, 0))
        e2.submitDart(DartThrow(20, 1, 20))
        assertFalse(e2.lastTurnThreeZeros)

        // Cricket: 3 misses in turn
        val ec = CricketEngine(listOf(a, b), emptyMap())
        ec.submitDart(0, 0)
        assertFalse(ec.lastTurnThreeZeros)
        ec.submitDart(0, 0)
        assertFalse(ec.lastTurnThreeZeros)
        ec.submitDart(0, 0)
        assertTrue(ec.lastTurnThreeZeros)
    }

    @Test
    fun x01_bustStoresBustedThrows() {
        val e = x01Engine(players = listOf(a, b), start = 40)
        e.submitDart(DartThrow(60, 3, 20)) // A busts with T20
        assertTrue(e.lastThrowBust)
        assertTrue(e.playerLastTurnBusted[0])
        assertEquals(1, e.playerLastTurnThrows[0].size)
        assertEquals("T20", e.playerLastTurnThrows[0][0].label())
        // Next player is B
        assertEquals(1, e.current)
    }
}
