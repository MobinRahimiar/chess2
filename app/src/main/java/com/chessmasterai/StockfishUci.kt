package com.chessmasterai

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One principal variation reported by a UCI-compatible engine. Scores are from the side-to-move POV. */
data class EngineLine(
    val rank: Int,
    val depth: Int,
    val score: String,
    val scoreIsMate: Boolean,
    val nodes: Long,
    val nps: Long,
    val pv: List<String>
) {
    val bestMove: String get() = pv.firstOrNull().orEmpty()
}

data class EngineReport(val lines: List<EngineLine>, val bestMove: String, val depth: Int)

data class StockfishSettings(
    val threads: Int = 2,
    val hashMb: Int = 96,
    val multiPv: Int = 3,
    val depth: Int = 14
) {
    fun normalized(availableProcessors: Int): StockfishSettings = copy(
        threads = threads.coerceIn(1, availableProcessors.coerceAtLeast(1)),
        hashMb = hashMb.coerceIn(16, 512),
        multiPv = multiPv.coerceIn(1, 5),
        depth = depth.coerceIn(1, 30)
    )
}

/**
 * Android process adapter for the official Stockfish Android executable.
 * Package it at app/src/main/jniLibs/arm64-v8a/libstockfish.so. Despite the
 * extension, this is an executable PIE binary, not an Android shared library.
 * The app deliberately reports unavailable rather than returning synthetic scores.
 */
class StockfishUci(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var outputLines: LinkedBlockingQueue<String>? = null

    fun engineFile(): File = File(appContext.applicationInfo.nativeLibraryDir, "libstockfish.so")

    fun isAvailable(): Boolean {
        val arm64Supported = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        val file = engineFile()
        return arm64Supported && file.isFile && file.canExecute()
    }

    fun analyze(
        fen: String,
        depth: Int = 14,
        multiPv: Int = 3,
        threads: Int = 2,
        hashMb: Int = 96,
        onStatus: (String) -> Unit,
        onReport: (EngineReport) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) {
            main.post { onStatus("Stockfish is already analyzing") }
            return
        }
        worker.execute {
            try {
                val binary = engineFile()
                if (!binary.isFile || !binary.canExecute()) {
                    throw IllegalStateException(
                        "Stockfish is not bundled for this device. Install the official ARM64 binary through the project build workflow."
                    )
                }
                val settings = StockfishSettings(threads, hashMb, multiPv, depth)
                    .normalized(Runtime.getRuntime().availableProcessors())
                // Validate the position before spawning a native process (invalid FEN must never reach it).
                val rootState = ChessEngine.fromFen(fen)
                require(ChessEngine.legalMoves(rootState).isNotEmpty()) {
                    "The supplied FEN is a terminal position; Stockfish has no legal move to analyze"
                }
                main.post { onStatus("Starting Stockfish…") }
                val p = ProcessBuilder(binary.absolutePath)
                    .directory(binary.parentFile)
                    .redirectErrorStream(true)
                    .start()
                process = p
                writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
                val queue = LinkedBlockingQueue<String>()
                outputLines = queue
                val reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
                Thread({
                    try {
                        while (true) queue.put(reader.readLine() ?: break)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (_: Exception) {
                        // Process termination is surfaced to the protocol loop as a closed stream.
                    } finally {
                        queue.offer(UciSession.EOF)
                        try { reader.close() } catch (_: Exception) { }
                    }
                }, "stockfish-output").apply { isDaemon = true; start() }

                val transport = object : UciTransport {
                    override fun send(command: String) = this@StockfishUci.send(command)
                    override fun nextLine(timeoutMs: Long): String? = this@StockfishUci.nextLine(timeoutMs)
                }
                val finalReport = UciSession.analyze(
                    transport, fen, settings,
                    onStatus = { msg -> main.post { onStatus(msg) } },
                    onReport = { snapshot -> main.post { onReport(snapshot) } }
                )
                main.post {
                    onReport(finalReport)
                    onStatus("Stockfish analysis complete")
                }
            } catch (t: Throwable) {
                main.post { onError(t.message ?: "Unknown Stockfish error") }
            } finally {
                try { send("quit") } catch (_: Exception) { }
                try { process?.destroy() } catch (_: Exception) { }
                process = null
                writer = null
                outputLines = null
                running.set(false)
            }
        }
    }

    fun stop() {
        if (running.get()) try { send("stop") } catch (_: Exception) { }
    }

    fun close() {
        stop()
        try { send("quit") } catch (_: Exception) { }
        try { process?.destroyForcibly() } catch (_: Exception) { }
        process = null
        worker.shutdownNow()
    }

    private fun send(command: String) {
        synchronized(this) {
            writer?.apply { write(command); newLine(); flush() }
        }
    }

    private fun nextLine(timeoutMs: Long): String? = outputLines?.poll(timeoutMs, TimeUnit.MILLISECONDS)

}

/** Parses one UCI `info` line; kept pure so protocol parsing can be unit-tested on the JVM. */
internal fun parseUciInfoLine(line: String): EngineLine? {
    val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    fun value(key: String): String? = tokens.indexOf(key)
        .takeIf { it >= 0 && it + 1 < tokens.size }?.let { tokens[it + 1] }
    val depth = value("depth")?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val rank = value("multipv")?.toIntOrNull()?.takeIf { it in 1..5 } ?: 1
    val scoreIndex = tokens.indexOf("score")
    if (scoreIndex < 0 || scoreIndex + 2 >= tokens.size) return null
    if (tokens.contains("lowerbound") || tokens.contains("upperbound")) return null // not an exact score
    val mate = tokens[scoreIndex + 1] == "mate"
    if (!mate && tokens[scoreIndex + 1] != "cp") return null
    val rawScore = tokens[scoreIndex + 2].toIntOrNull() ?: return null
    val score = if (mate) "Mate ${if (rawScore > 0) "+" else ""}$rawScore" else {
        val pawns = rawScore / 100.0
        String.format(Locale.US, "%s%.2f", if (pawns > 0) "+" else "", pawns)
    }
    val pvIndex = tokens.indexOf("pv")
    val pv = if (pvIndex >= 0 && pvIndex + 1 < tokens.size) tokens.drop(pvIndex + 1) else emptyList()
    return EngineLine(
        rank = rank,
        depth = depth,
        score = score,
        scoreIsMate = mate,
        nodes = value("nodes")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        nps = value("nps")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        pv = pv
    )
}
