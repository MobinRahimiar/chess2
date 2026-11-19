package com.chessmasterai

/** Complete local rules core for standard chess. Board indexes: 0=a8 ... 63=h1. */
data class ChessMove(val from: Int, val to: Int, val promotion: Char? = null, val enPassant: Boolean = false, val castle: Boolean = false)
data class ChessState(
    val board: List<Char>, val whiteToMove: Boolean = true,
    val castling: String = "KQkq", val enPassant: Int? = null,
    val halfmove: Int = 0, val fullmove: Int = 1
)

enum class GameResult { ACTIVE, CHECK, CHECKMATE_WHITE, CHECKMATE_BLACK, STALEMATE, DRAW_FIFTY_MOVE, DRAW_REPETITION, DRAW_INSUFFICIENT }

object ChessEngine {
    fun initial() = ChessState("rnbqkbnrpppppppp................................PPPPPPPPRNBQKBNR".toList())

    fun piece(state: ChessState, index: Int) = state.board.getOrElse(index) { '.' }
    fun colorOf(p: Char): Int = when { p == '.' -> 0; p.isUpperCase() -> 1; else -> -1 }
    private fun inside(r: Int, c: Int) = r in 0..7 && c in 0..7
    private fun idx(r: Int, c: Int) = r * 8 + c
    private fun row(i: Int) = i / 8
    private fun col(i: Int) = i % 8
    fun square(i: Int) = "${'a' + col(i)}${8 - row(i)}"

    fun isAttacked(s: ChessState, target: Int, by: Int): Boolean {
        val tr = row(target); val tc = col(target)
        for (i in 0..63) {
            val p = s.board[i]
            if (colorOf(p) != by) continue
            val r = row(i); val c = col(i); val type = p.lowercaseChar()
            if (type == 'p') {
                val dir = if (by == 1) -1 else 1
                if (r + dir == tr && kotlin.math.abs(c - tc) == 1) return true
            } else if (type == 'n') {
                if ((kotlin.math.abs(r-tr) == 2 && kotlin.math.abs(c-tc) == 1) || (kotlin.math.abs(r-tr) == 1 && kotlin.math.abs(c-tc) == 2)) return true
            } else if (type == 'k') {
                if (kotlin.math.max(kotlin.math.abs(r-tr), kotlin.math.abs(c-tc)) == 1) return true
            } else {
                val dr = tr-r; val dc = tc-c
                val aligned = when(type) { 'b' -> kotlin.math.abs(dr)==kotlin.math.abs(dc); 'r' -> dr==0 || dc==0; else -> dr==0 || dc==0 || kotlin.math.abs(dr)==kotlin.math.abs(dc) }
                if (!aligned || (dr==0 && dc==0)) continue
                val sr = dr.compareTo(0); val sc = dc.compareTo(0)
                var rr=r+sr; var cc=c+sc; var blocked=false
                while (rr!=tr || cc!=tc) { if (s.board[idx(rr,cc)]!='.') { blocked=true; break }; rr+=sr; cc+=sc }
                if (!blocked) return true
            }
        }
        return false
    }

    fun inCheck(s: ChessState, white: Boolean = s.whiteToMove): Boolean {
        val king = if (white) 'K' else 'k'
        val k = s.board.indexOf(king)
        return k >= 0 && isAttacked(s, k, if (white) -1 else 1)
    }

    private fun pseudo(s: ChessState): List<ChessMove> {
        val out = mutableListOf<ChessMove>(); val side = if (s.whiteToMove) 1 else -1
        fun add(from: Int, to: Int, ep: Boolean = false, castle: Boolean = false) {
            if (colorOf(s.board[to]) == side || s.board[to].lowercaseChar() == 'k') return
            val p = s.board[from]
            if (p.lowercaseChar() == 'p' && (row(to)==0 || row(to)==7)) listOf('q','r','b','n').forEach { out += ChessMove(from,to,if(side==1)it.uppercaseChar() else it,ep,castle) }
            else out += ChessMove(from,to,null,ep,castle)
        }
        for (from in 0..63) {
            val p=s.board[from]; if(colorOf(p)!=side) continue
            val r=row(from); val c=col(from); val type=p.lowercaseChar()
            if(type=='p') {
                val dir=if(side==1)-1 else 1; val nr=r+dir
                if(inside(nr,c) && s.board[idx(nr,c)]=='.') {
                    add(from,idx(nr,c)); val start=if(side==1)6 else 1
                    if(r==start && s.board[idx(r+2*dir,c)]=='.') add(from,idx(r+2*dir,c))
                }
                for(dc in listOf(-1,1)) { val nc=c+dc; if(!inside(nr,nc))continue; val to=idx(nr,nc)
                    if(colorOf(s.board[to])==-side) add(from,to)
                    else if(s.enPassant==to) out+=ChessMove(from,to,null,true,false)
                }
            } else {
                val deltas=when(type){ 'n'->listOf(-2 to -1,-2 to 1,-1 to -2,-1 to 2,1 to -2,1 to 2,2 to -1,2 to 1); 'k'->listOf(-1 to -1,-1 to 0,-1 to 1,0 to -1,0 to 1,1 to -1,1 to 0,1 to 1); 'b'->listOf(-1 to -1,-1 to 1,1 to -1,1 to 1); 'r'->listOf(-1 to 0,1 to 0,0 to -1,0 to 1); else->listOf(-1 to -1,-1 to 0,-1 to 1,0 to -1,0 to 1,1 to -1,1 to 0,1 to 1) }
                for((dr,dc) in deltas){ var rr=r+dr; var cc=c+dc
                    while(inside(rr,cc)){ val to=idx(rr,cc); if(colorOf(s.board[to])==side)break; add(from,to); if(s.board[to]!='.' || type=='n'||type=='k')break; rr+=dr;cc+=dc }
                }
                if(type=='k' && !inCheck(s,s.whiteToMove)) {
                    val home=if(side==1)60 else 4; val kingSide=if(side==1)"K" else "k"; val queenSide=if(side==1)"Q" else "q"
                    if(from==home && s.castling.contains(kingSide)) {
                        val rook=if(side==1)'R' else 'r'
                        if(s.board[home+3]==rook && s.board[home+1]=='.' && s.board[home+2]=='.' && !isAttacked(s,home+1,-side) && !isAttacked(s,home+2,-side)) add(from,home+2,castle=true)
                    }
                    if(from==home && s.castling.contains(queenSide)) {
                        val rook=if(side==1)'R' else 'r'
                        if(s.board[home-4]==rook && s.board[home-1]=='.' && s.board[home-2]=='.' && s.board[home-3]=='.' && !isAttacked(s,home-1,-side) && !isAttacked(s,home-2,-side)) add(from,home-2,castle=true)
                    }
                }
            }
        }
        return out
    }

    fun legalMoves(s: ChessState): List<ChessMove> = pseudo(s).filter { !inCheck(applyUnchecked(s,it),s.whiteToMove) }

    fun apply(s: ChessState, m: ChessMove): ChessState? = if (m in legalMoves(s)) applyUnchecked(s,m) else null

    /**
     * Applies a move WITHOUT re-validating it. Only call with a move that was just
     * returned by [legalMoves] for the same state (used by search code to avoid
     * regenerating the legal move list for every node).
     */
    fun applyLegal(s: ChessState, m: ChessMove): ChessState = applyUnchecked(s, m)

    private fun applyUnchecked(s: ChessState, m: ChessMove): ChessState {
        val b=s.board.toMutableList(); val moving=b[m.from]; val captured=b[m.to]; b[m.from]='.'
        b[m.to]=m.promotion ?: moving
        if(m.enPassant) b[m.to + if(s.whiteToMove)8 else -8]='.'
        if(m.castle) {
            if(m.to>m.from){ b[m.to-1]=b[m.to+1]; b[m.to+1]='.' }
            else { b[m.to+1]=b[m.to-2]; b[m.to-2]='.' }
        }
        var rights=s.castling
        fun remove(chars:String){ rights=rights.filterNot { it in chars } }
        when(m.from){60->remove("KQ");4->remove("kq");63->remove("K");56->remove("Q");7->remove("k");0->remove("q")}
        when(m.to){60->remove("KQ");4->remove("kq");63->remove("K");56->remove("Q");7->remove("k");0->remove("q")}
        val ep=if(moving.lowercaseChar()=='p' && kotlin.math.abs(m.to-m.from)==16)(m.to+m.from)/2 else null
        return ChessState(b,!s.whiteToMove,rights,ep,if(moving.lowercaseChar()=='p'||captured!='.'||m.enPassant)0 else s.halfmove+1,s.fullmove+if(!s.whiteToMove)1 else 0)
    }

    fun result(s: ChessState, repetitionCount:Int=1): GameResult {
        val moves=legalMoves(s); val check=inCheck(s)
        if(moves.isEmpty()) return if(check) if(s.whiteToMove) GameResult.CHECKMATE_BLACK else GameResult.CHECKMATE_WHITE else GameResult.STALEMATE
        if(insufficient(s)) return GameResult.DRAW_INSUFFICIENT
        if(s.halfmove>=100) return GameResult.DRAW_FIFTY_MOVE
        if(repetitionCount>=3) return GameResult.DRAW_REPETITION
        return if(check) GameResult.CHECK else GameResult.ACTIVE
    }
    private fun insufficient(s:ChessState):Boolean {
        val pieces=s.board.filter { it!='.' && it.lowercaseChar()!='k' }
        if(pieces.isEmpty()) return true
        if(pieces.size==1 && pieces[0].lowercaseChar() in listOf('b','n')) return true
        val bishops=s.board.indices.filter { s.board[it].lowercaseChar()=='b' }
        return pieces.all { it.lowercaseChar()=='b' } && bishops.map { (row(it)+col(it))%2 }.distinct().size==1
    }
    fun notation(s:ChessState,m:ChessMove):String {
        val p=s.board[m.from]; val capture=s.board[m.to]!='.'||m.enPassant
        if(m.castle)return if(m.to>m.from)"O-O" else "O-O-O"
        val prefix=if(p.lowercaseChar()=='p') if(capture) "${'a'+col(m.from)}x" else "" else "${p.uppercaseChar()}${if(capture)"x" else ""}"
        return prefix+square(m.to)+(m.promotion?.let { "=${it.uppercaseChar()}" } ?: "")
    }
    fun positionKey(s:ChessState):String = s.board.joinToString("")+"|${s.whiteToMove}|${s.castling}|${s.enPassant ?: -1}"

    fun fromFen(fen: String): ChessState {
        val fields = fen.trim().split(Regex("\\s+"))
        require(fields.size >= 4) { "Invalid FEN: expected at least four fields" }
        require(fields[1] == "w" || fields[1] == "b") { "Invalid FEN active color" }
        val ranks = fields[0].split("/")
        require(ranks.size == 8) { "Invalid FEN: expected eight ranks" }
        val board = mutableListOf<Char>()
        for (rank in ranks) {
            var count = 0
            for (ch in rank) {
                if (ch in '1'..'8') { count += ch.digitToInt(); repeat(ch.digitToInt()) { board += '.' } }
                else { require(ch in "pnbrqkPNBRQK") { "Invalid FEN piece: $ch" }; board += ch; count++ }
            }
            require(count == 8) { "Invalid FEN rank width" }
        }
        require(board.size == 64)
        val ep = if (fields[3] == "-") null else {
            require(fields[3].length == 2 && fields[3][0] in 'a'..'h' && fields[3][1] in '1'..'8')
            (8 - fields[3][1].digitToInt()) * 8 + (fields[3][0] - 'a')
        }
        val rights = fields[2].takeUnless { it == "-" }.orEmpty()
        require(rights.all { it in "KQkq" })
        val halfmove = fields.getOrNull(4)?.toIntOrNull() ?: 0
        val fullmove = fields.getOrNull(5)?.toIntOrNull() ?: 1
        require(halfmove >= 0 && fullmove >= 1) { "Invalid FEN move counters" }
        return ChessState(board, fields[1] == "w", rights, ep, halfmove, fullmove)
    }

    fun fen(s:ChessState):String {
        val ranks=(0..7).map { r -> var empty=0; buildString { for(c in 0..7){val p=s.board[idx(r,c)];if(p=='.')empty++ else {if(empty>0){append(empty);empty=0};append(p)}};if(empty>0)append(empty) } }.joinToString("/")
        return "$ranks ${if(s.whiteToMove)"w" else "b"} ${s.castling.ifEmpty { "-" }} ${s.enPassant?.let(::square) ?: "-"} ${s.halfmove} ${s.fullmove}"
    }
}
