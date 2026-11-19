package com.chessmasterai

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val Forest = Color(0xFF10251D)
private val Panel = Color(0xFF182F25)
private val Cream = Color(0xFFF0E6CC)
private val Gold = Color(0xFFD9AE52)
private val Muted = Color(0xFFB9C7B9)
private val LightSquare = Color(0xFFE8DDBF)
private val DarkSquare = Color(0xFF66865B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChessMasterApp() }
    }
}

@Composable
private fun ChessMasterApp() {
    var screen by remember { mutableStateOf("welcome") }
    var color by remember { mutableStateOf("White") }
    MaterialTheme(colorScheme = darkColorScheme(background = Forest, surface = Panel, primary = Gold, onPrimary = Color(0xFF17130A), onBackground = Cream, onSurface = Cream)) {
        Surface(Modifier.fillMaxSize(), color = Forest) {
            when (screen) {
                "welcome" -> WelcomeScreen(color, { color = it }, { screen = "game" }, { screen = "academy" })
                "academy" -> AcademyScreen { screen = "welcome" }
                else -> GameScreen(color, { screen = "welcome" })
            }
        }
    }
}

@Composable
private fun WelcomeScreen(selected: String, onSelect: (String) -> Unit, onStart: () -> Unit, onAcademy: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("♛", color = Gold, fontSize = 64.sp)
            Text("ChessMaster AI", color = Cream, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("PLAY  •  ANALYZE  •  LEARN  •  IMPROVE", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(26.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Welcome to ChessMaster AI", color = Cream, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Your chess companion for deeper analysis, tactical ideas and smarter training.", color = Muted, textAlign = TextAlign.Center)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose your side", color = Cream, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SideCard("♙", "White", selected == "White", Modifier.weight(1f)) { onSelect("White") }
                SideCard("♟", "Black", selected == "Black", Modifier.weight(1f)) { onSelect("Black") }
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF17130A))) { Text("Start Game  ▶", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            OutlinedButton(onClick = onAcademy, modifier = Modifier.fillMaxWidth()) { Text("♙  آکادمی شطرنج و تمرین", color = Cream) }
        }
        Text("Local chess rules • Engine-powered analysis when Stockfish is installed", color = Muted, fontSize = 11.sp)
    }
}


@Composable
private fun AcademyScreen(onBack: () -> Unit) {
    var puzzleIndex by remember { mutableIntStateOf(0) }
    var position by remember(puzzleIndex) { mutableStateOf(ChessEngine.fromFen(TrainingAcademy.puzzles[puzzleIndex].fen)) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var solved by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("حرکت مات را روی صفحه پیدا کن.") }
    val puzzle = TrainingAcademy.puzzles[puzzleIndex]
    val legal = remember(position) { ChessEngine.legalMoves(position) }
    val targets = remember(selected, legal) { legal.filter { it.from == selected }.map { it.to }.toSet() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Gold, fontSize = 32.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(10.dp)); Text("آکادمی شطرنج", color = Cream, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تمرین تاکتیکی", color = Gold, fontWeight = FontWeight.Bold)
                Text(puzzle.title, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${puzzle.theme} • نوبت سفید", color = Muted, fontSize = 11.sp)
                ChessBoard(position, selected, targets, true, solved, onSquare = { square ->
                    if (solved) return@ChessBoard
                    val piece = position.board[square]
                    if (selected == null) {
                        if (ChessEngine.colorOf(piece) == 1) selected = square
                    } else {
                        val move = legal.firstOrNull { it.from == selected && it.to == square }
                        if (move != null) {
                            if (TrainingAcademy.isSolved(puzzle, move)) {
                                position = ChessEngine.apply(position, move) ?: position
                                solved = true
                                feedback = "درست است! ${puzzle.explanation}"
                            } else {
                                feedback = "این حرکت جواب تمرین نیست. دوباره کیش‌ها، گرفتن‌ها و تهدیدها را بررسی کن."
                                selected = null
                            }
                        } else if (ChessEngine.colorOf(piece) == 1) selected = square else selected = null
                    }
                })
                Text(feedback, color = if (solved) Gold else Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { position = ChessEngine.fromFen(puzzle.fen); selected = null; solved = false; feedback = "حرکت مات را روی صفحه پیدا کن." }, modifier = Modifier.weight(1f)) { Text("شروع دوباره") }
                    Button(onClick = { feedback = "راهنما: شاه سیاه در h8 است؛ خانه‌های فرار و مسیر وزیر را بررسی کن." }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF17130A))) { Text("راهنمایی") }
                }
            }
        }
        Text("درس‌های کوتاه", color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        TrainingAcademy.lessons.forEach { lesson ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(lesson.title, color = Cream, fontWeight = FontWeight.Bold)
                    Text(lesson.summary, color = Muted, fontSize = 12.sp)
                    lesson.keyPoints.forEach { Text("• $it", color = Cream, fontSize = 11.sp) }
                }
            }
        }
        Text("تمرین فعلی یک پازل نمونهٔ تأییدشده با موتور قوانین داخلی است؛ بانک بزرگ پازل و سطح‌بندی تطبیقی در نسخه‌های بعدی اضافه می‌شود.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SideCard(piece: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(118.dp).border(if (active) 2.dp else 1.dp, if (active) Gold else Color(0xFF3D5145), RoundedCornerShape(16.dp)).background(if (active) Color(0xFF294633) else Panel, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(piece, fontSize = 42.sp, color = Cream); Text(label, color = Cream, fontWeight = FontWeight.Bold); Text(if (label == "White") "Play first" else "Play second", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun GameScreen(playerColor: String, onBack: () -> Unit) {
    var history by remember { mutableStateOf(listOf(ChessEngine.initial())) }
    var movesText by remember { mutableStateOf(emptyList<String>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var promotion by remember { mutableStateOf<ChessMove?>(null) }
    var engineStatus by remember { mutableStateOf("Stockfish engine not checked") }
    var engineError by remember { mutableStateOf<String?>(null) }
    var engineReport by remember { mutableStateOf<EngineReport?>(null) }
    var analysisDepth by remember { mutableIntStateOf(14) }
    var engineThreads by remember { mutableIntStateOf(2) }
    var engineHashMb by remember { mutableIntStateOf(96) }
    var engineMultiPv by remember { mutableIntStateOf(3) }
    val engineMaxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1).coerceAtMost(16)
    var predictionDepth by remember { mutableIntStateOf(4) }
    var predictionBusy by remember { mutableStateOf(false) }
    var predictionReport by remember { mutableStateOf<PredictionReport?>(null) }
    var predictionError by remember { mutableStateOf<String?>(null) }
    var explorerToken by remember { mutableStateOf("") }
    var explorerStats by remember { mutableStateOf<OpeningExplorerStats?>(null) }
    var explorerLoading by remember { mutableStateOf(false) }
    var explorerError by remember { mutableStateOf<String?>(null) }
    var explorerNote by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }
    var savedGames by remember { mutableStateOf(emptyList<SavedChessGame>()) }
    var pgnImportStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val predictionScope = rememberCoroutineScope()
    val library = remember { GameLibrary(context) }
    LaunchedEffect(library) { savedGames = library.all() }
    val pgnPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read selected file")
            }.mapCatching { library.importPgn(it) }
            pgnImportStatus = imported.fold(
                onSuccess = { count -> if (count > 0) "Imported $count PGN games into local opening statistics." else "No standard PGN games with moves were found." },
                onFailure = { "PGN import failed: ${it.message ?: "unknown error"}" }
            )
            savedGames = library.all()
        }
    }
    val stockfish = remember { StockfishUci(context) }
    val explorerCache = remember { ExplorerCache(PrefsExplorerStore(context)) }
    DisposableEffect(stockfish) { onDispose { stockfish.close() } }
    val state = history.last()
    val legal = remember(state) { ChessEngine.legalMoves(state) }
    val quickTactics = remember(state) { TacticalAnalyzer.analyze(state) }
    // Search-based findings (mate in two, poisoned captures, trap setups) run off the UI thread.
    var deepTactics by remember { mutableStateOf<List<TacticalFinding>>(emptyList()) }
    var deepTacticsRunning by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        deepTactics = emptyList()
        deepTacticsRunning = true
        deepTactics = runCatching { withContext(Dispatchers.Default) { TacticalSearch.deepFindings(state) } }.getOrDefault(emptyList())
        deepTacticsRunning = false
    }
    val tacticalFindings = remember(quickTactics, deepTactics) {
        (deepTactics + quickTactics)
            .distinctBy { "${it.motif}:${it.squares.sorted()}" }
            .sortedByDescending { it.severity }
            .take(12)
    }
    // Arrows: ranked candidate first moves (green/blue/amber), the opponent's best reply to line 1 and the opponent's threat (red).
    val predictionArrows = remember(predictionReport) {
        val arrows = mutableListOf<Triple<Int, Int, Color>>()
        val report = predictionReport
        if (report != null) {
            val palette = listOf(Color(0xFF4CAF50), Color(0xFF42A5F5), Color(0xFFFFB300))
            report.lines.take(3).forEachIndexed { i, line -> line.firstMove?.let { arrows.add(Triple(it.from, it.to, palette[i])) } }
            report.lines.firstOrNull()?.opponentReply?.let { arrows.add(Triple(it.from, it.to, Color(0xFFE57373))) }
            report.threat?.let { arrows.add(Triple(it.move.from, it.move.to, Color(0xFFD32F2F))) }
        }
        arrows.toList()
    }
    val openingSuggestions = remember(state, movesText, savedGames) { OpeningIntelligence.suggest(state, movesText, savedGames) }
    val targets = remember(selected, legal) { legal.filter { it.from == selected }.map { it.to }.toSet() }
    val suggestedMove = remember(state, engineReport?.bestMove, openingSuggestions) {
        engineReport?.bestMove?.let { uciToMove(state, it) } ?: openingSuggestions.firstOrNull()?.move
    }
    val isWhiteBottom = playerColor == "White"
    val result = ChessEngine.result(state, history.count { ChessEngine.positionKey(it) == ChessEngine.positionKey(state) })
    val status = when(result) {
        GameResult.CHECK -> if(state.whiteToMove) "White is in check" else "Black is in check"
        GameResult.CHECKMATE_WHITE -> "Checkmate — White wins"
        GameResult.CHECKMATE_BLACK -> "Checkmate — Black wins"
        GameResult.STALEMATE -> "Draw — Stalemate"
        GameResult.DRAW_FIFTY_MOVE -> "Draw — 50-move rule"
        GameResult.DRAW_REPETITION -> "Draw — Threefold repetition"
        GameResult.DRAW_INSUFFICIENT -> "Draw — Insufficient material"
        else -> if(state.whiteToMove) "White to move" else "Black to move"
    }
    fun makeMove(move: ChessMove) {
        val next = ChessEngine.apply(state, move) ?: return
        stockfish.stop()
        engineReport = null
        predictionReport = null
        predictionError = null
        engineError = null
        engineStatus = "Position changed — analyze again"
        explorerStats = null
        explorerError = null
        explorerNote = null
        val notation = ChessEngine.notation(state, move)
        history = history + next
        movesText = movesText + "${state.fullmove}${if(state.whiteToMove) "." else "..."} $notation"
        selected = null; promotion = null
    }
    // In the selected-side game mode, Stockfish automatically replies for the opponent.
    LaunchedEffect(state, playerColor) {
        val humanSideToMove = state.whiteToMove == (playerColor == "White")
        val finished = result.name.startsWith("CHECKMATE") || result.name.startsWith("DRAW") || result == GameResult.STALEMATE
        if (!humanSideToMove && !finished && stockfish.isAvailable()) {
            engineStatus = "AI opponent is thinking…"
            stockfish.analyze(
                fen = ChessEngine.fen(state), depth = analysisDepth, multiPv = 1,
                threads = engineThreads, hashMb = engineHashMb,
                onStatus = { engineStatus = it },
                onReport = { report ->
                    if (report.bestMove.isNotBlank()) {
                        uciToMove(state, report.bestMove)?.let { makeMove(it) }
                    }
                },
                onError = { engineError = it; engineStatus = "AI opponent unavailable" }
            )
        } else if (!humanSideToMove && !finished) {
            engineStatus = "Add the Android Stockfish binary to enable the AI opponent."
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Gold, fontSize = 32.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(10.dp)); Text("ChessMaster AI", color = Cream, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f)); Text(if(playerColor=="White") "♙ White" else "♟ Black", color = Muted)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Text(status, Modifier.fillMaxWidth().padding(12.dp), color = if(result==GameResult.CHECK || result.name.startsWith("CHECKMATE")) Gold else Cream, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        val urgentAlerts = tacticalFindings.filter {
            it.motif == TacticalMotif.MATE_THREAT || it.motif == TacticalMotif.MATE_IN_ONE ||
                it.motif == TacticalMotif.MATE_IN_TWO || it.motif == TacticalMotif.POISONED_CAPTURE
        }.take(2)
        if (urgentAlerts.isNotEmpty() && !result.name.startsWith("CHECKMATE")) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    urgentAlerts.forEach { alert ->
                        Text("⚠ ${alert.title}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(alert.description, color = Cream, fontSize = 11.sp)
                    }
                }
            }
        }
        ChessBoard(state, selected, targets, isWhiteBottom, result.name.startsWith("CHECKMATE") || result.name.startsWith("DRAW") || result==GameResult.STALEMATE, recommendedMove = suggestedMove, arrows = predictionArrows, tacticalHighlights = tacticalFindings.flatMap { it.squares }.toSet() + predictionReport?.lines.orEmpty().flatMap { line -> line.moves.take(1).flatMap { listOf(it.from, it.to) } }.toSet(), onSquare = { square ->
            if(result.name.startsWith("CHECKMATE") || result.name.startsWith("DRAW") || result==GameResult.STALEMATE) return@ChessBoard
            val p = state.board[square]
            if(selected == null) { if(ChessEngine.colorOf(p) == if(state.whiteToMove) 1 else -1) selected = square }
            else {
                val candidates = legal.filter { it.from == selected && it.to == square }
                when { candidates.size == 1 -> makeMove(candidates.first()); candidates.size > 1 -> promotion = candidates.first(); ChessEngine.colorOf(p) == if(state.whiteToMove) 1 else -1 -> selected = square; else -> selected = null }
            }
        })
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opening Intelligence", color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Offline book", color = Muted, fontSize = 10.sp)
                }
                if (openingSuggestions.isEmpty()) {
                    Text("No matching curated opening line at this position. Use engine analysis for general positions.", color = Muted, fontSize = 11.sp)
                } else {
                    openingSuggestions.take(4).forEachIndexed { index, suggestion ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. ${suggestion.san}  ·  ${suggestion.openingName} (${suggestion.eco})", color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("Repertoire priority ${suggestion.repertoireWeight}/100 · ${suggestion.localGames} saved-game occurrence(s) on this device", color = Muted, fontSize = 9.sp)
                            }
                            TextButton(onClick = { suggestion.move?.let { makeMove(it) } }, enabled = suggestion.move != null) {
                                Text("Play", color = Gold)
                            }
                        }
                    }
                    Text("Book priority is a curated reference score, not a win probability or live worldwide popularity statistic.", color = Muted, fontSize = 9.sp)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Master Opening Statistics", color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Lichess • online", color = Muted, fontSize = 10.sp)
                }
                Text("Optional live lookup of historical master-game results for this exact position. A Lichess read token is required by the current explorer service; it is used only for the request and is not saved. Downloaded statistics (public aggregates only) are kept on this device so the position can be viewed offline later.", color = Muted, fontSize = 10.sp)
                OutlinedTextField(
                    value = explorerToken,
                    onValueChange = { explorerToken = it; explorerError = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Lichess API token (not stored)") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Cream, unfocusedTextColor = Cream,
                        focusedBorderColor = Gold, unfocusedBorderColor = Muted,
                        focusedLabelColor = Gold, unfocusedLabelColor = Muted
                    )
                )
                Button(
                    onClick = {
                        explorerLoading = true
                        explorerError = null
                        predictionScope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { ExplorerCache.lookup(explorerCache, state, explorerToken, System.currentTimeMillis(), OpeningExplorerClient::fetchMasters) }
                            }
                            result.onSuccess { lookup ->
                                explorerStats = lookup.stats
                                val ageMin = ((System.currentTimeMillis() - lookup.savedAtMillis) / 60_000).coerceAtLeast(0)
                                explorerNote = if (lookup.fromCache) "Offline copy saved ${if (ageMin < 60) "$ageMin min" else "${ageMin / 60} h"} ago. ${lookup.warning.orEmpty()}" else "Fresh from Lichess and saved for offline use."
                            }.onFailure {
                                explorerError = it.message ?: "Opening statistics could not be loaded."
                            }
                            explorerLoading = false
                        }
                    },
                    enabled = !explorerLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF17130A))
                ) { Text(if (explorerLoading) "Loading master statistics…" else if (explorerToken.isBlank()) "Show saved (offline) statistics" else "Load master statistics") }
                explorerError?.let { Text(it, color = Color(0xFFFF9B8E), fontSize = 11.sp) }
                explorerNote?.let { Text(it, color = Muted, fontSize = 10.sp) }
                explorerStats?.let { stats ->
                    Text(
                        (stats.openingName ?: "Unclassified position") + (stats.eco?.let { " · $it" } ?: ""),
                        color = Cream, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Games: ${stats.games}   White wins: ${stats.percent(stats.whiteWins)}%   Draws: ${stats.percent(stats.draws)}%   Black wins: ${stats.percent(stats.blackWins)}%",
                        color = Muted, fontSize = 10.sp
                    )
                    if (stats.moves.isEmpty()) Text("No candidate continuations were returned for this position.", color = Muted, fontSize = 11.sp)
                    stats.moves.take(5).forEach { move ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(move.san, color = Cream, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                            Text("${move.games} games", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text("W ${if (move.games == 0L) 0 else move.whiteWins * 100 / move.games}%  D ${if (move.games == 0L) 0 else move.draws * 100 / move.games}%  B ${if (move.games == 0L) 0 else move.blackWins * 100 / move.games}%", color = Muted, fontSize = 9.sp)
                        }
                    }
                    Text("Statistics describe the Lichess master-game database, not a prediction of the result of your current game.", color = Muted, fontSize = 9.sp)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Tactical Intelligence", color = Gold, fontWeight = FontWeight.Bold)
                Text("Pattern scan + verified mate search (mate threats, mate in 2, poisoned captures) • heuristics are not engine lines", color = Muted, fontSize = 10.sp)
                if (deepTacticsRunning) Text("Calculating deeper tactics…", color = Muted, fontSize = 10.sp)
                if (tacticalFindings.isEmpty()) {
                    Text("No clear tactical pattern detected in this position. Run Stockfish for deeper calculation.", color = Muted, fontSize = 12.sp)
                } else {
                    tacticalFindings.take(5).forEach { finding ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(finding.title, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text("★".repeat(finding.severity.coerceIn(1, 5)), color = Gold, fontSize = 10.sp)
                            }
                            Text(finding.description, color = Muted, fontSize = 11.sp)
                            if (finding.squares.isNotEmpty()) {
                                Text("Squares: ${finding.squares.distinct().joinToString(" · ") { ChessEngine.square(it) }}", color = Gold, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF34483B))
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Multi-Move Prediction", color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${predictionDepth} ply", color = Muted, fontSize = 10.sp)
                }
                Text("Local legal-move search • minimax + alpha-beta • up to 5 candidate lines", color = Muted, fontSize = 10.sp)
                Slider(value = predictionDepth.toFloat(), onValueChange = { predictionDepth = it.toInt() }, valueRange = 1f..5f, steps = 3)
                Button(
                    onClick = {
                        predictionBusy = true
                        predictionError = null
                        predictionReport = null
                        val snapshot = state
                        val requestedDepth = predictionDepth
                        // Search off the UI thread; result is applied only if the position is unchanged.
                        predictionScope.launch {
                            val outcome = runCatching { withContext(Dispatchers.Default) { PredictionEngine.predict(snapshot, requestedDepth, 5) } }
                            if (state == snapshot) {
                                predictionReport = outcome.getOrNull()
                                predictionError = outcome.exceptionOrNull()?.message
                            }
                            predictionBusy = false
                        }
                    },
                    enabled = !predictionBusy && legal.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF17130A))
                ) { Text(if (predictionBusy) "Searching…" else "Predict continuations") }
                predictionError?.let { Text(it, color = Gold, fontSize = 11.sp) }
                predictionReport?.let { report ->
                    Text("Depth ${report.searchedDepthPlies} ply · ${report.nodes} nodes${if (report.truncated) " · limited" else ""}", color = Muted, fontSize = 10.sp)
                    report.lines.forEach { line ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${line.rank}. ${line.continuation}", color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            line.opponentReplySan?.let { Text("Opponent's best reply in this line: $it (a candidate, not a prediction)", color = Muted, fontSize = 10.sp) }
                            Text("Evaluation ${if (line.scoreCp >= 0) "+" else ""}${"%.2f".format(line.scoreCp / 100.0)} pawns${if (line.isMate) " · mate detected" else ""}", color = Gold, fontSize = 10.sp)
                            line.firstMove?.let { first ->
                                Text("First move: ${ChessEngine.square(first.from)} → ${ChessEngine.square(first.to)}", color = Muted, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF34483B))
                    }
                    report.threat?.let { Text("⚠ Opponent threat: ${it.san} — ${it.description}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    Text("Arrows: green/blue/amber = your top 3 first moves · light red = opponent's reply to line 1 · dark red = opponent threat", color = Muted, fontSize = 9.sp)
                    Text(report.explanation, color = Muted, fontSize = 10.sp)
                }
                Text("These are calculated candidate variations, not probabilities or guaranteed predictions. Stockfish remains the stronger engine when its real binary is installed.", color = Muted, fontSize = 9.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stockfish Analysis", color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Depth $analysisDepth", color = Muted, fontSize = 11.sp)
                }
                Slider(value = analysisDepth.toFloat(), onValueChange = { analysisDepth = it.toInt() }, valueRange = 8f..24f, steps = 15)
                Text("Threads: $engineThreads / $engineMaxThreads", color = Muted, fontSize = 11.sp)
                Slider(
                    value = engineThreads.toFloat(),
                    onValueChange = { engineThreads = it.toInt().coerceIn(1, engineMaxThreads) },
                    valueRange = 1f..engineMaxThreads.toFloat(),
                    steps = (engineMaxThreads - 2).coerceAtLeast(0)
                )
                Text("Hash: ${engineHashMb} MB", color = Muted, fontSize = 11.sp)
                Slider(value = engineHashMb.toFloat(), onValueChange = { engineHashMb = (it.toInt() / 16 * 16).coerceIn(16, 512) }, valueRange = 16f..512f, steps = 30)
                Text("MultiPV: $engineMultiPv lines", color = Muted, fontSize = 11.sp)
                Slider(value = engineMultiPv.toFloat(), onValueChange = { engineMultiPv = it.toInt().coerceIn(1, 5) }, valueRange = 1f..5f, steps = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        engineError = null
                        engineReport = null
                        stockfish.analyze(
                            fen = ChessEngine.fen(state), depth = analysisDepth, multiPv = engineMultiPv,
                            threads = engineThreads, hashMb = engineHashMb,
                            onStatus = { engineStatus = it },
                            onReport = { engineReport = it },
                            onError = { engineError = it; engineStatus = "Engine unavailable" }
                        )
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF17130A))) { Text("Analyze position") }
                    OutlinedButton(onClick = { stockfish.stop(); engineStatus = "Stopping analysis…" }, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
                Text(engineStatus, color = if (engineError == null) Muted else Gold, fontSize = 11.sp)
                engineError?.let { Text(it, color = Gold, fontSize = 11.sp) }
                engineReport?.let { report ->
                    Text("بهترین حرکت: ${report.bestMove}   •   عمق: ${report.depth}", color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(explainEngineLine(report.lines.firstOrNull(), state.whiteToMove), color = Muted, fontSize = 12.sp)
                    val suggested = remember(state, report.bestMove) { uciToMove(state, report.bestMove) }
                    if (suggested != null) {
                        Button(onClick = { makeMove(suggested) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF17130A))) {
                            Text("اجرای حرکت پیشنهادی موتور")
                        }
                    }
                    report.lines.forEach { line ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${line.rank}. ${line.score}  |  ${line.pv.take(8).joinToString(" ")}", color = Cream, fontSize = 11.sp)
                            Text(explainEngineLine(line, state.whiteToMove), color = Muted, fontSize = 10.sp)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF203B2D)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("مربی هوشمند فارسی", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            AiCoach.insights(state, report).forEachIndexed { index, insight ->
                                Text("${index + 1}. $insight", color = Cream, fontSize = 11.sp)
                            }
                            Text("این توضیحات از خطوط واقعی موتور استخراج شده‌اند؛ الگوهای تاکتیکی جداگانه ماهیت احتمالی دارند.", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
                if (engineError == null && !stockfish.isAvailable()) {
                    Text("Engine binary required: libstockfish.so (device ABI). No placeholder evaluation is shown.", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { if(history.size>1){history=history.dropLast(1);movesText=movesText.dropLast(1);selected=null;engineReport=null;predictionReport=null} }, enabled=history.size>1, modifier=Modifier.weight(1f), colors=ButtonDefaults.buttonColors(containerColor=Panel)) { Text("↶ Undo") }
            Button(onClick = { showSaveDialog = true }, modifier=Modifier.weight(1f), colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Color(0xFF17130A))) { Text("Save") }
            OutlinedButton(onClick = { history=listOf(ChessEngine.initial());movesText= emptyList();selected=null;promotion=null;engineReport=null;predictionReport=null;engineError=null }, modifier=Modifier.weight(1f)) { Text("Restart") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("My saved games", color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); Text("${savedGames.size}", color = Muted, fontSize = 11.sp)
                }
                OutlinedButton(onClick = { pgnPicker.launch(arrayOf("application/x-chess-pgn", "application/octet-stream", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import PGN database", color = Gold)
                }
                Text("Import public or personal .pgn files. Imported games improve local move-frequency suggestions; they are not engine evaluations.", color = Muted, fontSize = 10.sp)
                pgnImportStatus?.let { Text(it, color = Gold, fontSize = 10.sp) }
                if (savedGames.isEmpty()) Text("Saved games stay on this device. Save a game to see it here.", color = Muted, fontSize = 11.sp)
                savedGames.take(8).forEach { game ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(game.title, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("${game.moves.size} half-moves • ${game.playerColor}", color = Muted, fontSize = 10.sp)
                        }
                        if (game.fens.isNotEmpty()) TextButton(onClick = {
                            val restored = game.fens.mapNotNull { runCatching { ChessEngine.fromFen(it) }.getOrNull() }
                            if (restored.isNotEmpty()) { history = restored; movesText = game.moves; selected = null; promotion = null; engineReport = null; engineError = null; explorerStats = null; explorerError = null }
                        }) { Text("Load") } else Text("PGN data", color = Muted, fontSize = 10.sp)
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, library.toPgn(game)); putExtra(Intent.EXTRA_TITLE, "${game.title}.pgn") }
                            context.startActivity(Intent.createChooser(send, "Export PGN"))
                        }) { Text("Share PGN") }
                        TextButton(onClick = { library.delete(game.id); savedGames = library.all() }) { Text("Delete", color = Gold) }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier=Modifier.fillMaxWidth().weight(1f, fill=false)) {
            Column(Modifier.padding(12.dp)) {
                Text("Move history", color=Gold, fontWeight=FontWeight.Bold)
                Text(if(movesText.isEmpty()) "No moves yet" else movesText.takeLast(12).joinToString("   "), color=Muted, fontSize=12.sp)
                Spacer(Modifier.height(5.dp)); Text("FEN", color=Gold, fontSize=11.sp)
                Text(ChessEngine.fen(state), color=Muted, fontSize=10.sp)
            }
        }
        Text("Legal moves • Check detection • Castling • En passant • Promotion", color=Muted, fontSize=10.sp, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth())
    }
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save this game") },
            text = { OutlinedTextField(value = saveTitle, onValueChange = { saveTitle = it }, label = { Text("Game name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                library.save(saveTitle.ifBlank { "Game ${savedGames.size + 1}" }, playerColor, history, movesText)
                savedGames = library.all(); saveTitle = ""; showSaveDialog = false
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }
    if(promotion != null) {
        AlertDialog(onDismissRequest={promotion=null}, title={Text("Promote pawn")}, text={Text("Choose the piece for your pawn.")}, confirmButton={
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf('q','r','b','n').forEach { piece -> TextButton(onClick={ val base=promotion!!; makeMove(base.copy(promotion=if(state.whiteToMove)piece.uppercaseChar() else piece)) }) { Text(when(piece){'q'->"Queen";'r'->"Rook";'b'->"Bishop";else->"Knight"}) } } }
        }, dismissButton={TextButton(onClick={ promotion=null }) { Text("Cancel") }})
    }
}

@Composable
private fun ChessBoard(state: ChessState, selected: Int?, targets: Set<Int>, whiteBottom: Boolean, disabled: Boolean, recommendedMove: ChessMove? = null, tacticalHighlights: Set<Int> = emptySet(), arrows: List<Triple<Int, Int, Color>> = emptyList(), onSquare: (Int)->Unit) {
    val order = if(whiteBottom) (0..63).toList() else (63 downTo 0).toList()
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
    Column(Modifier.fillMaxSize().border(2.dp, Gold, RoundedCornerShape(4.dp))) {
        for(r in 0..7) Row(Modifier.weight(1f)) {
            for(c in 0..7) {
                val index=order[r*8+c]; val piece=state.board[index]
                val light=((index/8)+(index%8))%2==0
                val isTarget=index in targets
                val isBestFrom = index == recommendedMove?.from
                val isBestTo = index == recommendedMove?.to
                val isTactical = index in tacticalHighlights
                val baseSquare = if (isBestFrom) Color(0xFFB99A3B) else if (isBestTo) Color(0xFF6F9D45) else if (isTactical) Color(0xFF8B6338) else if(light) LightSquare else DarkSquare
                Box(Modifier.weight(1f).fillMaxHeight().background(baseSquare).then(if(index==selected)Modifier.border(3.dp,Gold) else if(isBestFrom || isBestTo)Modifier.border(3.dp,Color(0xFFFFD65A)) else if(isTactical)Modifier.border(2.dp,Color(0xFFFFC66D)) else Modifier).clickable { onSquare(index) }, contentAlignment=Alignment.Center) {
                    if(isTarget) Box(Modifier.size(if(piece=='.')10.dp else 30.dp).border(3.dp,Color(0xFF244B2D),RoundedCornerShape(if(piece=='.')50 else 5)))
                    if(isBestFrom || isBestTo) Text(if(isBestFrom) "●" else "◆", Modifier.align(Alignment.TopEnd).padding(2.dp), color=Color(0xFF30240A), fontSize=9.sp)
                    if(piece!='.') Text(glyph(piece),fontSize=if(c==0)27.sp else 31.sp,color=if(piece.isUpperCase())Color(0xFFFFFEF5) else Color(0xFF171717),textAlign=TextAlign.Center)
                    if(c==0) Text("${8-index/8}",Modifier.align(Alignment.TopStart).padding(start=2.dp,top=1.dp),fontSize=8.sp,color=Color(0xFF242424))
                    if(r==7) Text("${'a'+index%8}",Modifier.align(Alignment.BottomEnd).padding(end=2.dp,bottom=0.dp),fontSize=8.sp,color=Color(0xFF242424))
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.width / 8f
        fun center(index: Int): Offset {
            val pos = order.indexOf(index)
            return Offset((pos % 8 + 0.5f) * cell, (pos / 8 + 0.5f) * cell)
        }
        arrows.forEach { (from, to, color) ->
            if (from == to) return@forEach
            val a = center(from); val b = center(to)
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len < 1f) return@forEach
            val ux = dx / len; val uy = dy / len
            val head = cell * 0.32f
            val tip = b
            val baseX = tip.x - ux * head; val baseY = tip.y - uy * head
            val shaftEnd = Offset(baseX, baseY)
            drawLine(color.copy(alpha = 0.85f), a, shaftEnd, strokeWidth = cell * 0.12f)
            val wing = head * 0.6f
            drawLine(color.copy(alpha = 0.85f), tip, Offset(baseX - uy * wing, baseY + ux * wing), strokeWidth = cell * 0.12f)
            drawLine(color.copy(alpha = 0.85f), tip, Offset(baseX + uy * wing, baseY - ux * wing), strokeWidth = cell * 0.12f)
        }
    }
    }
}

private fun glyph(piece: Char): String = when(piece) {
    'K'->"♔";'Q'->"♕";'R'->"♖";'B'->"♗";'N'->"♘";'P'->"♙"
    'k'->"♚";'q'->"♛";'r'->"♜";'b'->"♝";'n'->"♞";'p'->"♟"
    else->""
}
