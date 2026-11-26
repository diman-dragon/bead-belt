package dev.beadbelt.game
import java.io.File
fun main(args: Array<String>) {
    val out = File(args[1]); out.delete()
    for (line in File(args[0]).readLines()) {
        val p = line.split(" ").map { it.toInt() }
        val id = p[0]; val att = p[1]
        var b = minOf(30, p[2] + if (id <= 10) 2 else if (id <= 30) 1 else 0)
        while (b < 30 && !pass(id, att, b)) b++
        val ok = pass(id, att, b)
        out.appendText("$id $att $b ${if (ok) "OK" else "BAD"}\n")
    }
}
