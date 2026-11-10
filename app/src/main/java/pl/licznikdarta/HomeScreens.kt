package pl.licznikdarta

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import kotlin.random.Random

@Composable
fun StartScreen(
    state: AppState,
    onStartGame: (Screen) -> Unit,
    onResumeGame: (SavedGame) -> Unit,
    onDeleteSavedGame: (String) -> Unit = {},
    onFinishSavedGame: (SavedGame) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedGameTypeIndex by remember { mutableStateOf(if (state.lastSetup.type == GameType.CRICKET) 1 else 0) }
    val isCricket = selectedGameTypeIndex == 1

    // X01 options
    var x01Points by remember { mutableStateOf(state.lastSetup.x01Points) }
    var x01CheckOut by remember { mutableStateOf(state.lastSetup.x01CheckOut) }
    var x01CheckIn by remember { mutableStateOf(state.lastSetup.x01CheckIn) }
    var x01SetLegMode by remember { mutableStateOf(state.lastSetup.x01SetLegMode) }
    var x01Sets by remember { mutableStateOf(state.lastSetup.x01Sets) }
    var x01Legs by remember { mutableStateOf(state.lastSetup.x01Legs) }

    // X01 dropdown expansion states
    var expandedPunkty by remember { mutableStateOf(false) }
    var expandedCheckOut by remember { mutableStateOf(false) }
    var expandedSety by remember { mutableStateOf(false) }
    var expandedSetLeg by remember { mutableStateOf(false) }
    var expandedCheckIn by remember { mutableStateOf(false) }
    var expandedLegi by remember { mutableStateOf(false) }

    // Cricket options
    var cricketPointsOn by remember { mutableStateOf(state.lastSetup.cricketPointsOn) }
    var cricketCutThroat by remember { mutableStateOf(state.lastSetup.cricketCutThroat) }
    var cricketSetLegMode by remember { mutableStateOf(state.lastSetup.cricketSetLegMode) }
    var cricketSets by remember { mutableStateOf(state.lastSetup.cricketSets) }
    var cricketLegs by remember { mutableStateOf(state.lastSetup.cricketLegs) }

    // Cricket dropdown expansion states
    var expandedCricketPoints by remember { mutableStateOf(false) }
    var expandedCricketMode by remember { mutableStateOf(false) }
    var expandedCricketSety by remember { mutableStateOf(false) }
    var expandedCricketSetLeg by remember { mutableStateOf(false) }
    var expandedCricketLegi by remember { mutableStateOf(false) }

    // Common match setup
    var randomOrder by remember { mutableStateOf(state.lastSetup.randomOrder) }
    var useBot by remember { mutableStateOf(state.lastSetup.useBot) }
    var botLevel by remember { mutableStateOf(state.lastSetup.botLevel) }

    // Match lineup (player IDs) - zapamiętany skład z poprzedniej rozgrywki
    val lineup = remember {
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

    LaunchedEffect(lineup.toList()) {
        if (lineup.isNotEmpty()) {
            state.saveLastLineup(lineup.toList())
        }
    }

    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showBotDialog by remember { mutableStateOf(false) }
    var showWarning by remember { mutableStateOf(false) }
    var gameToFinish by remember { mutableStateOf<SavedGame?>(null) }
    var gameToDelete by remember { mutableStateOf<String?>(null) }

    val saved = state.loadSaved()

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        // TOP BAR: "Dart Scoreboard" + Share icon
        AppTopBar(
            title = "Dart Scoreboard",
            onBack = null,
            actions = {
                IconButton(onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Gram w darta z aplikacją Dart Scoreboard!")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Udostępnij"))
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Udostępnij",
                        tint = DartGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // TAB SELECTOR: [ X01 ] [ Cricket ]
            item {
                Spacer(Modifier.height(4.dp))
                SegmentedTabs(
                    items = listOf("X01", "Cricket"),
                    selectedIndex = selectedGameTypeIndex,
                    onSelect = { selectedGameTypeIndex = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            // GAME OPTIONS
            item {
                if (!isCricket) {
                    // X01 OPTIONS: 2 rows of 3 dropdown columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        DropdownOptionColumn(
                            label = "Punkty",
                            selectedValue = x01Points.toString(),
                            options = listOf(101, 201, 301, 401, 501, 601, 701, 801, 901, 1001),
                            isExpanded = expandedPunkty,
                            onToggleExpand = { expandedPunkty = !expandedPunkty },
                            onSelectOption = {
                                x01Points = it
                                expandedPunkty = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownOptionColumn(
                            label = "Check-Out",
                            selectedValue = x01CheckOut.pl,
                            options = listOf(CheckOutMode.STRAIGHT, CheckOutMode.DOUBLE, CheckOutMode.MASTER),
                            optionLabel = { it.pl },
                            isExpanded = expandedCheckOut,
                            onToggleExpand = { expandedCheckOut = !expandedCheckOut },
                            onSelectOption = {
                                x01CheckOut = it
                                expandedCheckOut = false
                            },
                            color = DropdownRed,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownOptionColumn(
                            label = "Sety",
                            selectedValue = x01Sets.toString(),
                            options = listOf(1, 2, 3, 4, 5),
                            isExpanded = expandedSety,
                            onToggleExpand = { expandedSety = !expandedSety },
                            onSelectOption = {
                                x01Sets = it
                                expandedSety = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        DropdownOptionColumn(
                            label = "Set/Leg",
                            selectedValue = x01SetLegMode.pl,
                            options = listOf(SetLegMode.FIRST_TO, SetLegMode.BEST_OF),
                            optionLabel = { it.pl },
                            isExpanded = expandedSetLeg,
                            onToggleExpand = { expandedSetLeg = !expandedSetLeg },
                            onSelectOption = {
                                x01SetLegMode = it
                                expandedSetLeg = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownOptionColumn(
                            label = "Check-In",
                            selectedValue = x01CheckIn.pl,
                            options = listOf(CheckInMode.STRAIGHT, CheckInMode.DOUBLE, CheckInMode.MASTER),
                            optionLabel = { it.pl },
                            isExpanded = expandedCheckIn,
                            onToggleExpand = { expandedCheckIn = !expandedCheckIn },
                            onSelectOption = {
                                x01CheckIn = it
                                expandedCheckIn = false
                            },
                            color = DropdownRed,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownOptionColumn(
                            label = "Legi",
                            selectedValue = x01Legs.toString(),
                            options = listOf(1, 2, 3, 4, 5),
                            isExpanded = expandedLegi,
                            onToggleExpand = { expandedLegi = !expandedLegi },
                            onSelectOption = {
                                x01Legs = it
                                expandedLegi = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // CRICKET OPTIONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        DropdownOptionColumn(
                            label = "Punkty",
                            selectedValue = if (cricketPointsOn) "Wł" else "Wył",
                            options = listOf("Wł", "Wył"),
                            isExpanded = expandedCricketPoints,
                            onToggleExpand = { expandedCricketPoints = !expandedCricketPoints },
                            onSelectOption = {
                                cricketPointsOn = (it == "Wł")
                                expandedCricketPoints = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownOptionColumn(
                            label = "Tryb",
                            selectedValue = if (cricketCutThroat) "Cut Throat" else "Normalny",
                            options = listOf("Normalny", "Cut Throat"),
                            isExpanded = expandedCricketMode,
                            onToggleExpand = { expandedCricketMode = !expandedCricketMode },
                            onSelectOption = {
                                cricketCutThroat = (it == "Cut Throat")
                                expandedCricketMode = false
                            },
                            color = DropdownRed,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownOptionColumn(
                            label = "Sety",
                            selectedValue = cricketSets.toString(),
                            options = listOf(1, 2, 3, 4, 5),
                            isExpanded = expandedCricketSety,
                            onToggleExpand = { expandedCricketSety = !expandedCricketSety },
                            onSelectOption = {
                                cricketSets = it
                                expandedCricketSety = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        DropdownOptionColumn(
                            label = "Set/Leg",
                            selectedValue = cricketSetLegMode.pl,
                            options = listOf(SetLegMode.FIRST_TO, SetLegMode.BEST_OF),
                            optionLabel = { it.pl },
                            isExpanded = expandedCricketSetLeg,
                            onToggleExpand = { expandedCricketSetLeg = !expandedCricketSetLeg },
                            onSelectOption = {
                                cricketSetLegMode = it
                                expandedCricketSetLeg = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f))
                        DropdownOptionColumn(
                            label = "Legi",
                            selectedValue = cricketLegs.toString(),
                            options = listOf(1, 2, 3, 4, 5),
                            isExpanded = expandedCricketLegi,
                            onToggleExpand = { expandedCricketLegi = !expandedCricketLegi },
                            onSelectOption = {
                                cricketLegs = it
                                expandedCricketLegi = false
                            },
                            color = DropdownGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // RESUME SAVED GAMES (all active games in progress)
            if (state.activeGames.isNotEmpty()) {
                item {
                    Text(
                        "Trwające gry (${state.activeGames.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(state.activeGames.size) { idx ->
                    val ag = state.activeGames[idx]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = DartCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DartGreen.copy(alpha = 0.8f))
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DartGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = ag.summary.ifEmpty { ag.type.pl },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    val pText = if (ag.progress.isNotEmpty()) {
                                        ag.progress.joinToString(" · ") { "${it.name}: ${it.score}" }
                                    } else {
                                        ag.players.joinToString(", ") { it.name }
                                    }
                                    Text(pText, fontSize = 12.sp, color = DartSubtle)
                                }
                                IconButton(
                                    onClick = { gameToDelete = ag.id },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Usuń grę",
                                        tint = DartRed.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { gameToFinish = ag },
                                    colors = ButtonDefaults.buttonColors(containerColor = DartOrange),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Zakończ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onResumeGame(ag) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Wznów ▶", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }

            // BIG RED START BUTTON
            item {
                RedStartButton(
                    text = "START",
                    onClick = {
                        val activeLineup = lineup.filter { id -> state.players.any { it.id == id } }
                        if (activeLineup.isEmpty() && !useBot) {
                            showWarning = true
                            return@RedStartButton
                        }
                        var matchPlayers = activeLineup.mapNotNull { id -> state.players.find { it.id == id } }
                        val botsMap = mutableMapOf<String, BotLevel>()
                        if (useBot) {
                            val bot = Player(name = "DartBot")
                            matchPlayers = matchPlayers + bot
                            botsMap[bot.id] = botLevel
                        }
                        val currentSetup = LastGameSetup(
                            type = if (isCricket) GameType.CRICKET else GameType.X01,
                            x01Points = x01Points,
                            x01CheckIn = x01CheckIn,
                            x01CheckOut = x01CheckOut,
                            x01SetLegMode = x01SetLegMode,
                            x01Sets = x01Sets,
                            x01Legs = x01Legs,
                            cricketPointsOn = cricketPointsOn,
                            cricketCutThroat = cricketCutThroat,
                            cricketSetLegMode = cricketSetLegMode,
                            cricketSets = cricketSets,
                            cricketLegs = cricketLegs,
                            randomOrder = randomOrder,
                            useBot = useBot,
                            botLevel = botLevel,
                            lineup = activeLineup
                        )
                        state.saveLastSetup(currentSetup)
                        state.saveLastLineup(activeLineup)
                        if (randomOrder) {
                            matchPlayers = matchPlayers.shuffled(Random)
                        }
                        state.currentActiveGameId = java.util.UUID.randomUUID().toString()
                        if (!isCricket) {
                            onStartGame(
                                Screen.PlayX01(
                                    X01Engine(
                                        players = matchPlayers,
                                        bots = botsMap,
                                        startScore = x01Points,
                                        checkIn = x01CheckIn,
                                        checkOut = x01CheckOut,
                                        legsTarget = x01Legs,
                                        setsTarget = x01Sets,
                                        setLegMode = x01SetLegMode
                                    )
                                )
                            )
                        } else {
                            onStartGame(
                                Screen.PlayCricket(
                                    CricketEngine(
                                        players = matchPlayers,
                                        bots = botsMap,
                                        pointsOn = cricketPointsOn,
                                        cutThroat = cricketCutThroat,
                                        legsTarget = cricketLegs,
                                        setsTarget = cricketSets,
                                        setLegMode = cricketSetLegMode
                                    )
                                )
                            )
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // LINEUP CONTROLS ROW: [v] Losowa kolejnosc  [+Bot]  [+Dodaj]
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { randomOrder = !randomOrder },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = randomOrder,
                            onCheckedChange = { randomOrder = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DartGreen,
                                checkmarkColor = Color.Black,
                                uncheckedColor = DartSubtle
                            )
                        )
                        Text(
                            text = "Losowa kolejność",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // +Bot button
                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clickable {
                                    useBot = !useBot
                                    if (useBot) showBotDialog = true
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (useBot) DartGreen else DartGreen.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "Bot",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // +Dodaj player button
                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .clickable { showAddPlayerDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = DartGreen
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Dodaj gracza",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Dodaj", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // "Gracze" header
            item {
                Text(
                    text = "Gracze",
                    fontSize = 14.sp,
                    color = DartSubtle,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Current lineup players list
            val matchPlayers = lineup.mapNotNull { id -> state.players.find { it.id == id } }
            if (matchPlayers.isEmpty() && !useBot) {
                item {
                    Text(
                        "Brak wybranych graczy. Kliknij '+ Dodaj' aby dodać graczy do gry.",
                        fontSize = 13.sp,
                        color = DartSubtle,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(matchPlayers) { index, player ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DartIcon(
                            color = if (index == 0) DartGreen else DartSubtle,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = player.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { lineup.remove(player.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Usuń",
                                tint = DartSubtle,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = {
                            // Move down or cycle order
                            if (lineup.size > 1) {
                                val curIdx = lineup.indexOf(player.id)
                                val nextIdx = (curIdx + 1) % lineup.size
                                lineup.removeAt(curIdx)
                                lineup.add(nextIdx, player.id)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Kolejność",
                                tint = DartSubtle,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (useBot) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "DartBot",
                                tint = DartGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "DartBot",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Poziom: ${botLevel.pl}",
                                    fontSize = 12.sp,
                                    color = DartSubtle
                                )
                            }
                            IconButton(onClick = { useBot = false }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Usuń bota",
                                    tint = DartSubtle,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Player Dialog (select existing or type new)
    if (showAddPlayerDialog) {
        var newPlayerName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPlayerDialog = false },
            title = { Text("Wybierz lub dodaj graczy") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPlayerName,
                            onValueChange = { newPlayerName = it },
                            label = { Text("Nowy gracz") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val n = newPlayerName.trim()
                                if (n.isNotEmpty()) {
                                    val newP = Player(name = n)
                                    state.savePlayers(state.players + newP)
                                    lineup.add(newP.id)
                                    newPlayerName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                            enabled = newPlayerName.isNotBlank()
                        ) {
                            Text("Dodaj", color = Color.Black)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Zapisani gracze:", fontSize = 13.sp, color = DartSubtle)
                    Spacer(Modifier.height(6.dp))

                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        itemsIndexed(state.players) { _, p ->
                            val isChecked = lineup.contains(p.id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) lineup.remove(p.id) else lineup.add(p.id)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) lineup.remove(p.id) else lineup.add(p.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DartGreen,
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(p.name, fontSize = 15.sp, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddPlayerDialog = false }) {
                    Text("Gotowe", color = DartGreen)
                }
            }
        )
    }

    // Bot Dialog (Level selection)
    if (showBotDialog) {
        AlertDialog(
            onDismissRequest = { showBotDialog = false },
            title = { Text("Poziom DartBota") },
            text = {
                Column {
                    listOf(BotLevel.EASY, BotLevel.NORMAL, BotLevel.HARD).forEach { level ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    botLevel = level
                                    showBotDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = botLevel == level,
                                onClick = {
                                    botLevel = level
                                    showBotDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = DartGreen)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(level.pl, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBotDialog = false }) { Text("OK", color = DartGreen) }
            }
        )
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Brak graczy") },
            text = { Text("Dodaj co najmniej jednego gracza lub włącz bota, aby rozpocząć grę.") },
            confirmButton = {
                TextButton(onClick = { showWarning = false }) { Text("OK", color = DartGreen) }
            }
        )
    }

    if (gameToFinish != null) {
        val ag = gameToFinish!!
        AlertDialog(
            onDismissRequest = { gameToFinish = null },
            title = { Text("Zakończyć grę?") },
            text = { Text("Czy na pewno chcesz zakończyć tę grę teraz i zapisać jej końcowy wynik na podium?") },
            confirmButton = {
                Button(
                    onClick = {
                        val g = gameToFinish
                        gameToFinish = null
                        if (g != null) onFinishSavedGame(g)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartOrange)
                ) {
                    Text("Zakończ grę i podsumuj", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToFinish = null }) {
                    Text("Anuluj", color = DartSubtle)
                }
            }
        )
    }

    if (gameToDelete != null) {
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            title = { Text("Usunąć grę?") },
            text = { Text("Czy na pewno chcesz usunąć tę trwającą grę bez zapisu wyników?") },
            confirmButton = {
                Button(
                    onClick = {
                        val idDel = gameToDelete
                        gameToDelete = null
                        if (idDel != null) onDeleteSavedGame(idDel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartRed)
                ) {
                    Text("Usuń", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) {
                    Text("Anuluj", color = DartSubtle)
                }
            }
        )
    }
}

/**
 * Ekran "Gracze" (Screenshot 4)
 */
@Composable
fun PlayersScreen(state: AppState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Player?>(null) }
    var editing by remember { mutableStateOf<Player?>(null) }
    var editName by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(DartDarkBg)
    ) {
        // TOP BAR: "Gracze" + "+" icon
        AppTopBar(
            title = "Gracze",
            onBack = null,
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Dodaj gracza",
                        tint = DartGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(state.players) { index, player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editing = player
                            editName = player.name
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DartIcon(
                        color = if (index == 0) DartGreen else DartSubtle,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = player.name,
                        fontSize = 17.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { confirmDelete = player }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = DartSubtle,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = {
                        // Reorder/drag handle
                        if (state.players.size > 1) {
                            val nextIdx = (index + 1) % state.players.size
                            val list = state.players.toMutableList()
                            val item = list.removeAt(index)
                            list.add(nextIdx, item)
                            state.savePlayers(list)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Przesuń",
                            tint = DartSubtle,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (state.players.isEmpty()) {
                item {
                    Text(
                        text = "Brak graczy. Kliknij '+' w prawym górnym rogu, aby dodać pierwszego gracza.",
                        fontSize = 14.sp,
                        color = DartSubtle,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Dodaj gracza") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Imię gracza") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val n = newName.trim()
                        if (n.isNotEmpty()) {
                            state.savePlayers(state.players + Player(name = n))
                            newName = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen),
                    enabled = newName.isNotBlank()
                ) {
                    Text("Dodaj", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Anuluj", color = DartSubtle) }
            }
        )
    }

    editing?.let { p ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edytuj gracza") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Imię") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val n = editName.trim()
                        if (n.isNotEmpty()) {
                            state.savePlayers(state.players.map {
                                if (it.id == p.id) it.copy(name = n) else it
                            })
                        }
                        editing = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartGreen)
                ) {
                    Text("Zapisz", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Anuluj", color = DartSubtle) }
            }
        )
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Usunąć gracza?") },
            text = { Text("Gracz ${p.name} oraz jego gry zostaną usunięte.") },
            confirmButton = {
                Button(
                    onClick = {
                        state.savePlayers(state.players.filter { it.id != p.id })
                        state.removeGamesWithPlayer(p.name)
                        confirmDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DartRed)
                ) {
                    Text("Usuń", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Anuluj", color = DartSubtle) }
            }
        )
    }
}


/**
 * Rozwijana kolumna opcji meczowych (menu rozwijalne w stylu akordeonu)
 */
@Composable
fun <T> DropdownOptionColumn(
    label: String,
    selectedValue: String,
    options: List<T>,
    optionLabel: (T) -> String = { it.toString() },
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectOption: (T) -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        val headerShape = if (isExpanded) {
            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        } else {
            RoundedCornerShape(4.dp)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onToggleExpand),
            shape = headerShape,
            color = color
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedValue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        if (isExpanded) {
            val dropdownShape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = 4.dp,
                bottomEnd = 4.dp
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = dropdownShape,
                color = color
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (options.size > 5) it.heightIn(max = 195.dp).verticalScroll(rememberScrollState())
                            else it
                        }
                ) {
                    options.forEachIndexed { index, option ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.8.dp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }
                        val optText = optionLabel(option)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clickable {
                                    onSelectOption(option)
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
