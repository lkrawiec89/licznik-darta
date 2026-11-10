package pl.licznikdarta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 15.sp) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
fun SetupScreen(state: AppState, type: GameType, onStart: (Screen) -> Unit, onBack: () -> Unit) {
    val selected = remember {
        mutableStateListOf<String>().apply {
            val preferred = if (state.lastSetup.lineup.isNotEmpty()) state.lastSetup.lineup else state.lastLineup
            val savedValid = preferred.filter { id -> state.players.any { it.id == id } }
            if (savedValid.isNotEmpty()) {
                addAll(savedValid)
            } else if (state.players.isNotEmpty()) {
                addAll(state.players.take(3).map { it.id })
            }
        }
    }
    var points by remember { mutableStateOf(if (type == GameType.X01) state.lastSetup.x01Points.toString() else "Punkty: Wł") }
    var checkIn by remember { mutableStateOf(state.lastSetup.x01CheckIn.pl) }
    var checkOut by remember { mutableStateOf(state.lastSetup.x01CheckOut.pl) }
    var setLegMode by remember { mutableStateOf(if (type == GameType.CRICKET) state.lastSetup.cricketSetLegMode.pl else state.lastSetup.x01SetLegMode.pl) }
    var legs by remember { mutableStateOf(if (type == GameType.CRICKET) state.lastSetup.cricketLegs.toString() else state.lastSetup.x01Legs.toString()) }
    var sets by remember { mutableStateOf(if (type == GameType.CRICKET) state.lastSetup.cricketSets.toString() else state.lastSetup.x01Sets.toString()) }
    var cricketPoints by remember { mutableStateOf(if (state.lastSetup.cricketPointsOn) "Punkty: Wł" else "Punkty: Wył") }
    var cricketMode by remember { mutableStateOf(if (state.lastSetup.cricketCutThroat) "Cut Throat" else "Normal") }
    var doublesOnly by remember { mutableStateOf(false) }
    var useBot by remember { mutableStateOf(state.lastSetup.useBot) }
    var botLevel by remember { mutableStateOf(state.lastSetup.botLevel) }
    var randomOrder by remember { mutableStateOf(state.lastSetup.randomOrder) }
    var warn by remember { mutableStateOf(false) }

    val pointsItems = (101..1001 step 100).map { it.toString() }
    val checkInItems = CheckInMode.entries.map { it.pl }
    val checkOutItems = CheckOutMode.entries.map { it.pl }
    val setLegItems = SetLegMode.entries.map { it.pl }
    val legsItems = (1..21).map { it.toString() }
    val cricketPointsItems = listOf("Punkty: Wł", "Punkty: Wył")
    val cricketModeItems = listOf("Normal", "Cut Throat")

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Nowa gra: ${type.pl}", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

            when (type) {
                GameType.X01 -> {
                    Text("OPCJE GRY", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    DropdownField("Punkty", points, pointsItems) { points = it }
                    DropdownField("Wejście (Check-In)", checkIn, checkInItems) { checkIn = it }
                    DropdownField("Wyjście (Check-Out)", checkOut, checkOutItems) { checkOut = it }
                    DropdownField("Tryb setów/legów", setLegMode, setLegItems) { setLegMode = it }
                    DropdownField("Legi", legs, legsItems) { legs = it }
                    DropdownField("Sety", sets, legsItems) { sets = it }
                }
                GameType.CRICKET -> {
                    Text("OPCJE GRY", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    DropdownField("Punktacja", cricketPoints, cricketPointsItems) { cricketPoints = it }
                    DropdownField("Tryb Cricket", cricketMode, cricketModeItems) { cricketMode = it }
                    DropdownField("Tryb setów/legów", setLegMode, setLegItems) { setLegMode = it }
                    DropdownField("Legi", legs, legsItems) { legs = it }
                    DropdownField("Sety", sets, legsItems) { sets = it }
                    Text("Zamykaj sektory 20-15 i BULL. Cut Throat: punkty dostają przeciwnicy.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GameType.AROUND -> {
                    Text("OPCJE GRY", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tylko podwójne (tryb trudny)", modifier = Modifier.weight(1f))
                        Switch(checked = doublesOnly, onCheckedChange = { doublesOnly = it })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("GRACZE", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (state.players.isEmpty()) {
                Text("Najpierw dodaj graczy (ekran Gracze).", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }
            state.players.forEachIndexed { index, p ->
                Row(Modifier.fillMaxWidth().clickable {
                    if (selected.contains(p.id)) selected.remove(p.id) else selected.add(p.id)
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected.contains(p.id), onCheckedChange = {
                        if (selected.contains(p.id)) selected.remove(p.id) else selected.add(p.id)
                    })
                    Text("${index + 1}.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(p.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("BOT (DartBot)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Graj przeciwko botowi", modifier = Modifier.weight(1f))
                Switch(checked = useBot, onCheckedChange = { useBot = it })
            }
            if (useBot) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(BotLevel.EASY, BotLevel.NORMAL, BotLevel.HARD).forEach { l ->
                        FilterChip(selected = botLevel == l, onClick = { botLevel = l }, label = { Text(l.pl) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Losowa kolejność graczy", modifier = Modifier.weight(1f))
                Switch(checked = randomOrder, onCheckedChange = { randomOrder = it })
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                val activeSelected = selected.filter { id -> state.players.any { it.id == id } }
                if (activeSelected.isEmpty()) { warn = true; return@Button }
                val currentSetup = LastGameSetup(
                    type = type,
                    x01Points = points.toIntOrNull() ?: state.lastSetup.x01Points,
                    x01CheckIn = CheckInMode.entries.firstOrNull { it.pl == checkIn } ?: state.lastSetup.x01CheckIn,
                    x01CheckOut = CheckOutMode.entries.firstOrNull { it.pl == checkOut } ?: state.lastSetup.x01CheckOut,
                    x01SetLegMode = SetLegMode.entries.firstOrNull { it.pl == setLegMode } ?: state.lastSetup.x01SetLegMode,
                    x01Sets = sets.toIntOrNull() ?: state.lastSetup.x01Sets,
                    x01Legs = legs.toIntOrNull() ?: state.lastSetup.x01Legs,
                    cricketPointsOn = cricketPoints == "Punkty: Wł",
                    cricketCutThroat = cricketMode == "Cut Throat",
                    cricketSetLegMode = SetLegMode.entries.firstOrNull { it.pl == setLegMode } ?: state.lastSetup.cricketSetLegMode,
                    cricketSets = sets.toIntOrNull() ?: state.lastSetup.cricketSets,
                    cricketLegs = legs.toIntOrNull() ?: state.lastSetup.cricketLegs,
                    randomOrder = randomOrder,
                    useBot = useBot,
                    botLevel = botLevel,
                    lineup = activeSelected
                )
                state.saveLastSetup(currentSetup)
                state.saveLastLineup(activeSelected)
                var players = activeSelected.mapNotNull { id -> state.players.find { it.id == id } }
                val bots = mutableMapOf<String, BotLevel>()
                if (useBot) {
                    val bot = Player(name = "DartBot")
                    players = players + bot
                    bots[bot.id] = botLevel
                }
                if (randomOrder) players = players.shuffled(Random)
                state.currentActiveGameId = java.util.UUID.randomUUID().toString()
                when (type) {
                    GameType.X01 -> onStart(Screen.PlayX01(X01Engine(players, bots,
                        points.toInt(), CheckInMode.entries.first { it.pl == checkIn },
                        CheckOutMode.entries.first { it.pl == checkOut },
                        legs.toInt(), sets.toInt(), SetLegMode.entries.first { it.pl == setLegMode })))
                    GameType.CRICKET -> onStart(Screen.PlayCricket(CricketEngine(players, bots,
                        pointsOn = cricketPoints == "Punkty: Wł",
                        cutThroat = cricketMode == "Cut Throat",
                        legsTarget = legs.toInt(), setsTarget = sets.toInt(),
                        setLegMode = SetLegMode.entries.first { it.pl == setLegMode })))
                    GameType.AROUND -> onStart(Screen.PlayAround(AroundEngine(players, bots, doublesOnly)))
                }
            }, enabled = state.players.isNotEmpty() && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("START GRY", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (warn) {
        AlertDialog(onDismissRequest = { warn = false },
            title = { Text("Brak graczy") },
            text = { Text("Zaznacz co najmniej jednego gracza, aby rozpocząć grę.") },
            confirmButton = { TextButton(onClick = { warn = false }) { Text("OK") } })
    }
}
