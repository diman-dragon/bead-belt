package dev.beadbelt.game

import java.io.File

/** Структурная проверка раскладки; возвращает список нарушений. */
fun audit(l: Level): List<String> {
    val e = ArrayList<String>()
    val s = Levels.spec(l.id)
    val tray = IntArray(COLOR_COUNT); val box = IntArray(COLOR_COUNT)
    l.columns.flatten().forEach { tray[it.c] += it.n; if (it.n !in 3..6) e.add("лоток ${it.n}") }
    l.boxes.forEach { box[it]++ }
    for (c in 0 until COLOR_COUNT) if (tray[c] != box[c] * BOX) e.add("баланс цвета $c")
    if (l.boxes.size != s.colors * s.perColor) e.add("число коробок")
    if (l.columns.size != s.cols) e.add("число колонок")
    if ((l.elevator >= 0) != s.lift) e.add("лифт")
    var hid = 0
    l.columns.forEach { col -> col.forEachIndexed { i, t -> if (t.hidden) { hid++; if (i == 0) e.add("скрыт первый лоток") } } }
    if (s.hiddenShare <= 0.0 && hid > 0) e.add("скрытые лотки до урока")
    if (s.hiddenLesson && (hid != 2 || l.columns.take(2).any { !it[1].hidden })) e.add("учебные скрытые лотки не на месте")
    if (s.hiddenShare > 0.0 && hid == 0) e.add("нет скрытых лотков")
    val pos = HashMap<Int, ArrayList<Triple<Int, Int, TrayDef>>>()
    l.columns.forEachIndexed { ci, col -> col.forEachIndexed { ti, t ->
        if (t.pid == 0 && t.arrow != 0) e.add("стрелка без пары")
        if (t.pid != 0) pos.getOrPut(t.pid) { ArrayList() }.add(Triple(ci, ti, t))
        if (t.arrow !in 0..2) e.add("arrow=${t.arrow}")
    } }
    if (pos.size != s.pairs) e.add("пар ${pos.size}, надо ${s.pairs}")
    for ((pid, v) in pos) {
        if (v.size != 2) { e.add("pid $pid: ${v.size} лотков"); continue }
        val leads = v.filter { it.third.arrow != 0 }
        if (leads.size != 1) { e.add("pid $pid: ведущих ${leads.size}"); continue }
        val lead = leads[0]; val fol = v.first { it !== lead }
        if (Math.abs(lead.first - fol.first) != 1) e.add("pid $pid: не соседние колонки")
        val want = if (fol.first > lead.first) Links.RIGHT else Links.LEFT
        if (lead.third.arrow != want) e.add("pid $pid: стрелка смотрит не на ведомого")
        if (s.sameOnly && lead.third.c != fol.third.c) e.add("pid $pid: учебная пара разных цветов")
    }
    if (s.headFirst) {
        var active = false
        for (i in l.columns.indices) {
            val h = l.columns[i][0]
            if (Links.follower(h.arrow, h.pid, i, l.columns.size) { c -> l.columns[c].firstOrNull()?.pid ?: 0 } >= 0) active = true
        }
        if (!active) e.add("учебная связь не стоит впереди")
    }
    return e
}

fun play(level: Level, seed: Long): Boolean {
    val dt = 1f / 60f
    val g = Game(level, seed) { _, _ -> }
    var t = 0f; var idle = 0f
    while (g.state == State.PLAY && t < 900f) {
        idle += dt
        if (idle > 0.4f && Hints.calm(g)) {
            var col = Hints.advise(g).col
            if (col < 0) col = g.cols.indices.firstOrNull { g.cols[it].isNotEmpty() } ?: -1
            if (col >= 0) { g.tapColumn(col); idle = 0f }
        }
        g.update(dt); t += dt
    }
    return g.state == State.WON
}

/** ≥ 28 из 30 на seed 1..30 и ≥ 4 из 5 на seed 1..5. */
fun pass(id: Int, attempt: Int, belt: Int): Boolean {
    var losses = 0; var early = 0
    for (seed in 1L..30L) {
        val ok = play(Levels.layout(id, attempt, belt), seed)
        if (!ok) { losses++; if (seed <= 5) early++ }
        if (losses > 2 || early > 1) return false
    }
    return true
}

fun main(args: Array<String>) {
    val from = args[0].toInt(); val to = args[1].toInt(); val out = File(args[2])
    for (id in from..to) {
        var done = false
        for (attempt in 0..30) {
            val l = Levels.layout(id, attempt, 30)
            val bad = audit(l)
            if (bad.isNotEmpty()) { System.err.println("уровень $id попытка $attempt: $bad"); continue }
            val mc = Planner.minCap(l) ?: continue
            var b = maxOf(15 - id / 17, mc)
            var found = -1
            while (b <= 30) {
                if (pass(id, attempt, b) && pass(id, attempt, b + 1).let { it || b + 1 > 30 }) { found = b; break }
                b++
            }
            if (found < 0) continue
            val fin = minOf(30, found + 1)           // итог — вторая из двух подряд проходящих лент
            out.appendText("$id $attempt $fin $mc\n")
            done = true; break
        }
        if (!done) out.appendText("$id FAIL\n")
    }
}
