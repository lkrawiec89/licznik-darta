package pl.licznikdarta

import java.util.UUID

enum class GameType(val pl: String) {
    X01("X01"),
    CRICKET("Cricket"),
    AROUND("Dookoła tarczy")
}

enum class BotLevel(val pl: String) {
    NONE("Wyłączony"),
    EASY("Łatwy"),
    NORMAL("Średni"),
    HARD("Trudny")
}

enum class CheckInMode(val pl: String, val en: String) {
    STRAIGHT("Straight In", "Straight In"),
    DOUBLE("Double In", "Double In"),
    MASTER("Master In", "Master In")
}

enum class CheckOutMode(val pl: String, val en: String) {
    STRAIGHT("Straight Out", "Straight Out"),
    DOUBLE("Double Out", "Double Out"),
    MASTER("Master Out", "Master Out")
}

enum class SetLegMode(val pl: String, val en: String) {
    FIRST_TO("First to", "First to"),
    BEST_OF("Best of", "Best of")
}

data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)

/** Pojedyncza lotka. miss=true gdy pudlo. */
data class DartThrow(val value: Int, val mult: Int, val segment: Int) {
    val isMiss: Boolean get() = value == 0
    val isDouble: Boolean get() = (segment in 1..20 && mult == 2) || (segment == 25 && mult == 2)
    val isTriple: Boolean get() = segment in 1..20 && mult == 3

    fun label(): String = when {
        mult == 0 || value == 0 -> "0"
        mult == 2 -> if (segment == 25) "BULL" else "D$segment"
        mult == 3 -> "T$segment"
        else -> segment.toString()
    }
}

data class Turn(val playerIdx: Int, val darts: Int, val score: Int)

data class Perf(
    val throws: Int = 0,
    val points: Int = 0,
    val bestTurn: Int = 0,
    val first9: Int = 0,
    val marks: Int = 0,
    val doubles: Int = 0,
    val triples: Int = 0,
    val legsWon: Int = 0,
    val legsPlayed: Int = 0,
    val maxCheckout: Int = 0,
    val minDartsLeg: Int = 0,
    val p60: Int = 0,
    val p100: Int = 0,
    val p140: Int = 0,
    val p180: Int = 0,
    val checkoutAttempts: Int = 0,
    val hitsBySector: Map<String, Int> = emptyMap()
)

data class GameRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: GameType,
    val date: Long = System.currentTimeMillis(),
    val players: List<String>,
    val winner: String,
    val places: List<String>,
    val summary: String,
    val perPlayer: Map<String, Perf>
)

enum class KeySound(val pl: String) {
    MECHANICAL("Mechaniczny (maszyna)"),
    MODERN("Krótki klik"),
    WOOD("Drewniany stuk"),
    HEAVY("Przemysłowy styk"),
    BEEP("Elektroniczny beep")
}

data class LastGameSetup(
    val type: GameType = GameType.X01,
    val x01Points: Int = 501,
    val x01CheckIn: CheckInMode = CheckInMode.STRAIGHT,
    val x01CheckOut: CheckOutMode = CheckOutMode.DOUBLE,
    val x01SetLegMode: SetLegMode = SetLegMode.FIRST_TO,
    val x01Sets: Int = 1,
    val x01Legs: Int = 1,
    val cricketPointsOn: Boolean = true,
    val cricketCutThroat: Boolean = false,
    val cricketSetLegMode: SetLegMode = SetLegMode.FIRST_TO,
    val cricketSets: Int = 1,
    val cricketLegs: Int = 1,
    val randomOrder: Boolean = false,
    val useBot: Boolean = false,
    val botLevel: BotLevel = BotLevel.NORMAL,
    val lineup: List<String> = emptyList()
)

data class Settings(
    val sounds: Boolean = true,
    val voice: Boolean = false,
    val botLevel: BotLevel = BotLevel.NORMAL,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val fontScale: Float = 1.0f,
    val keySound: KeySound = KeySound.MECHANICAL,
    val autoCheckUpdates: Boolean = true,
    val updateUrl: String = ""
)

data class PlayerProgress(
    val name: String,
    val score: Int,
    val setsWon: Int = 0,
    val legsWon: Int = 0
)

data class SavedGame(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val type: GameType,
    val summary: String,
    val engineJson: String,
    val bots: Map<String, BotLevel>,
    val players: List<Player>,
    val progress: List<PlayerProgress> = emptyList(),
    val inProgress: Boolean = true
)
