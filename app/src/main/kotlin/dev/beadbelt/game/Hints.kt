package dev.beadbelt.game

/** Совет по текущей партии. */
class Advice(val kind: Int, val col: Int = -1) {
    companion object {
        const val TAP = 0       // нажми на колонку col
        const val WAIT = 1      // подожди: шарики ещё едут или коробки лопаются
        const val STUCK = 2     // из этого положения выиграть нельзя
        const val UNSURE = 3    // не удалось просчитать до конца: ход col — разумный, но без гарантии
        const val NONE = 4      // игра окончена
    }
}

/**
 * Подсказки: настоящая партия переводится в дискретную модель [Planner], и поиск запускается
 * от *текущего* положения. Поэтому совет учитывает все прошлые ошибки игрока, а не только исходный уровень.
 */
object Hints {
    /** Живая партия → модель (доска + состояние). Шарики в воронке и очереди считаются уже лежащими на ленте. */
    fun snapshot(g: Game): Pair<Board, PState> {
        val cols = g.cols.map { col -> col.map { TrayDef(it.c, it.n, it.arrow, it.pid) } }
        val stacks = ArrayList<IntArray>(SLOTS)
        val actN = IntArray(SLOTS)
        for (i in 0 until SLOTS) {
            val list = ArrayList<Int>()
            val head = g.boxes[i]
            if (head != null) { list.add(head.c); actN[i] = head.committed }
            for (q in g.queues[i]) list.add(q.c)
            stacks.add(list.toIntArray())
        }
        val board = Board(cols, stacks, if (g.elevatorIsOpen) -1 else g.level.elevator, g.n)
        val belt = IntArray(COLOR_COUNT)
        var total = 0
        for (c in g.slots) if (c >= 0) { belt[c]++; total++ }
        for (i in 0 until g.hopper.size) { belt[g.hopper[i].c]++; total++ }
        for (s in g.spawnQ) { belt[s.c]++; total++ }
        val st = PState(IntArray(cols.size), IntArray(SLOTS), actN, belt, false, total, 0)
        Planner.settle(board, st)
        return board to st
    }

    /**
     * Всё ли успокоилось: ничего не падает и не летит, а шарики на ленте больше не едут ни к какой коробке.
     * Только тогда совет точен: пока шарики в пути, неизвестно, кому они достанутся.
     */
    fun calm(g: Game): Boolean {
        if (!(g.spawnQ.isEmpty() && g.hopper.isEmpty() && g.flyers.isEmpty() && g.rockets.isEmpty())) return false
        for (c in g.slots) {
            if (c < 0) continue
            for (b in g.boxes) if (b != null && b.c == c && !b.locked && b.committed < BOX) return false
        }
        return true
    }

    /**
     * Что делать сейчас. `limit` — сколько состояний модели разобрать (мало — быстро, но чаще «не уверен»).
     * Вызывать можно с любого потока на «замороженной» партии; сама игра не меняется.
     */
    fun advise(g: Game, limit: Int = 60_000): Advice {
        if (g.state != State.PLAY) return Advice(Advice.NONE)
        if (!calm(g)) return Advice(Advice.WAIT)
        val (board, st) = snapshot(g)
        if (st.done == board.totalBoxes) return Advice(Advice.WAIT)
        val out = Planner.solve(board, st, limit)
        if (out.win && out.path.isNotEmpty()) return Advice(Advice.TAP, out.path[0])
        if (out.exhausted) return Advice(Advice.STUCK)
        // не хватило лимита: берём ход, после которого на ленте меньше всего шариков
        var best = -1; var bestTotal = Int.MAX_VALUE
        for (c in board.cols.indices) {
            val ns = Planner.tap(board, st, c) ?: continue
            if (ns.total < bestTotal) { bestTotal = ns.total; best = c }
        }
        return if (best >= 0) Advice(Advice.UNSURE, best) else Advice(Advice.STUCK)
    }
}
