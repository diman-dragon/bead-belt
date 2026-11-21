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
    /**
     * Живая партия → модель (доска + состояние). Шарики в воронке и очереди считаются уже лежащими на ленте,
     * а закреплённые за коробками — уже собранными: так же поступает сама игра (см. `Game.assign`).
     */
    fun snapshot(g: Game): Pair<Board, PState> {
        val cols = g.cols.map { col -> col.map { TrayDef(it.c, it.n, it.arrow) } }
        val stacks = ArrayList<IntArray>(SLOTS)
        val actN = IntArray(SLOTS)
        for (i in 0 until SLOTS) {
            val list = ArrayList<Int>()
            val head = g.boxes[i]
            if (head != null && head.committed < BOX) { list.add(head.c); actN[i] = head.committed }
            for (q in g.queues[i]) {
                if (q.committed >= BOX) continue                       // полностью забронирована — для модели уже собрана
                if (list.isEmpty()) actN[i] = q.committed
                list.add(q.c)
            }
            stacks.add(list.toIntArray())
        }
        val board = Board(cols, stacks, if (g.elevatorIsOpen) -1 else g.level.elevator, g.n)
        val belt = IntArray(COLOR_COUNT)
        var total = 0
        for (j in g.slots.indices) { val c = g.slots[j]; if (c >= 0 && g.claimOf[j] == null) { belt[c]++; total++ } }
        for (i in 0 until g.hopper.size) { val m = g.hopper[i]; if (m.claim == null) { belt[m.c]++; total++ } }
        for (s in g.spawnQ) if (s.claim == null) { belt[s.c]++; total++ }
        val st = PState(IntArray(cols.size), IntArray(SLOTS), actN, belt, false, total, 0)
        Planner.settle(board, st)
        return board to st
    }

    /**
     * Всё ли успокоилось: ничего не падает, не летит и не лопается, а закреплённые шарики доехали до коробок.
     * Совет в такой момент точный: модель и игра сейчас в одном и том же состоянии.
     */
    fun calm(g: Game): Boolean {
        if (!(g.spawnQ.isEmpty() && g.hopper.isEmpty() && g.flyers.isEmpty() && g.rockets.isEmpty() &&
                g.boxes.none { it != null && it.pop >= 0f })) return false
        for (j in g.slots.indices) if (g.claimOf[j] != null) return false
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
