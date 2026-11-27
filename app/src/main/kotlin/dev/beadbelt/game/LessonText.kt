package dev.beadbelt.game

/**
 * Тексты уроков: карточка с объяснением перед учебным уровнем и строка приза после него.
 * Всплывающих подсказок в игре нет, поэтому задание («что нажать») входит в саму карточку.
 * Карточка вмещает до ~7 строк по ~35 знаков.
 */
object LessonText {
    fun title(ls: Lesson) = when (ls.id) {
        LS_BASICS -> tr("Как играть", "How to play")
        LS_BULB -> tr("Подсказка", "The hint")
        LS_UNDO -> tr("Отменить ход", "Undo")
        LS_CLEAR -> tr("Очистить ленту", "Clear the belt")
        LS_SHUFFLE -> tr("Перемешать", "Shuffle")
        LS_HIDDEN -> tr("Скрытый цвет", "Hidden color")
        LS_LINKS -> tr("Связанные лотки", "Linked trays")
        LS_MIXED -> tr("Два цвета в лотке", "Two-color trays")
        LS_CHAIN -> tr("Лоток в цепи", "Chained trays")
        LS_MYSTERY -> tr("Скрытые коробки", "Mystery boxes")
        else -> tr("Коробка с замком", "Padlocked box")
    }

    fun body(ls: Lesson) = when (ls.id) {
        LS_BASICS -> tr(
            "Нажми на лоток вверху: шарики высыплются в воронку и поедут по ленте. Шарик сам влетит в коробку своего цвета, когда проедет мимо. В коробке три места. Собери все коробки!",
            "Tap a tray at the top: its beads pour into the funnel and ride the belt. A bead jumps into the box of its color as it passes. A box holds three. Fill every box!")
        LS_BULB -> tr(
            "Не знаешь, что нажать? Подсказка просчитает партию, покажет пальцем лоток, который ведёт к победе, и обведёт коробки, куда пойдут его шарики. Сейчас она бесплатная: нажми на неё внизу. За урок получишь три подсказки.",
            "Not sure what to tap? The hint analyzes the position, points at the tray that leads to a win and outlines the boxes its beads will go to. It is free now: tap it below. Finish the level to get three hints.")
        LS_UNDO -> tr(
            "Ошибся? Стрелка назад вернёт всё как было перед последним ходом. Сначала нажми на любой лоток, потом на стрелку внизу. Сейчас бесплатно, за урок получишь три отмены.",
            "Made a mistake? The back arrow restores everything to just before your last move. Tap any tray first, then the arrow below. Free now; finish the level to get three undos.")
        LS_CLEAR -> tr(
            "Лента вмещает ограниченное число шариков. Забьётся и ни одна коробка не подойдёт: проигрыш. Метла снимает все шарики с ленты и возвращает их в лотки. Насыпь немного и нажми на метлу внизу.",
            "The belt holds a limited number of beads. If it jams and no box matches, you lose. The broom takes every bead off the belt and puts it back into trays. Pour a few and tap the broom below.")
        LS_SHUFFLE -> tr(
            "Лотки встали неудачно? «Перемешать» раздаёт оставшиеся лотки по колонкам заново и подбирает расклад, из которого уровень проходим. Связи при этом разрываются. Нажми на значок внизу.",
            "Trays lined up badly? Shuffle deals the remaining trays into the columns again and looks for a deal that can be won. Links are broken by it. Tap the icon below.")
        LS_HIDDEN -> tr(
            "Серый лоток с «?» прячет цвет. Какой он, станет видно, только когда лоток выйдет вперёд своей колонки. Планируй с оглядкой: следующий лоток может оказаться любым цветом.",
            "A grey tray with a question mark hides its color. You see it only once the tray reaches the front of its column. Plan carefully: the next tray can be any color.")
        LS_LINKS -> tr(
            "Лотки в золотой рамке связаны. Нажми на ведущий лоток (от него идёт стрелка), и ведомый высыплется вместе с ним. Нажмёшь на ведомый, высыплется только он: назад связь не работает.",
            "Trays in a golden frame are linked. Tap the leading tray (the arrow starts there) and the follower pours out with it. Tap the follower and only it pours: the link never works backwards.")
        LS_MIXED -> tr(
            "Бывают лотки из двух цветов: часть шариков одного цвета, часть другого. Все они ссыпаются на ленту вместе, а число шариков каждого цвета в уровне не меняется. Смотри на шарики в лотке.",
            "Some trays hold two colors: a few beads of one and a few of another. They all pour onto the belt together, and the total of each color in the level stays the same. Look at the beads in the tray.")
        LS_CHAIN -> tr(
            "Лоток в цепи не высыпается. На значке написано, сколько коробок надо собрать, чтобы цепь спала. Пока она держит, нажимай на другие лотки: цепь только дрожит.",
            "A chained tray will not pour. The badge shows how many boxes you must fill to break the chain. Until then tap other trays: the chain just rattles.")
        LS_MYSTERY -> tr(
            "Серая коробка с «?» ждёт в очереди: её цвет станет виден, только когда она выйдет на место. Не рассчитывай на следующую коробку в стопке: она может оказаться любого цвета.",
            "A grey box with a question mark waits in the queue: its color shows only once it moves into place. Do not count on the next box in a stack: it can be any color.")
        else -> tr(
            "Коробка с замком пока не принимает шарики. Собери любую другую коробку, и замок откроется. Выбирай, что собирать первым.",
            "A padlocked box does not accept beads yet. Fill any other box and the lock opens. Choose what to fill first.")
    }

    fun ok(ls: Lesson) = if (ls.id == LS_BASICS) tr("Поехали", "Let's go") else tr("Попробовать", "Try it")
    fun tag() = tr("Новое", "New")

    /** Строка приза для итоговой карточки. */
    fun prize(ls: Lesson): String {
        val p = ls.prize
        val parts = ArrayList<String>(5)
        if (p.coins > 0) parts.add(tr("+${p.coins} монет", "+${p.coins} coins"))
        if (p.aids[AID_HINT] > 0) parts.add(tr("подсказка ×${p.aids[AID_HINT]}", "hint ×${p.aids[AID_HINT]}"))
        if (p.aids[AID_UNDO] > 0) parts.add(tr("отмена ×${p.aids[AID_UNDO]}", "undo ×${p.aids[AID_UNDO]}"))
        if (p.aids[AID_SHUFFLE] > 0) parts.add(tr("перемешать ×${p.aids[AID_SHUFFLE]}", "shuffle ×${p.aids[AID_SHUFFLE]}"))
        if (p.aids[AID_CLEAR] > 0) parts.add(tr("очистка ×${p.aids[AID_CLEAR]}", "clear ×${p.aids[AID_CLEAR]}"))
        return tr("Приз: ", "Prize: ") + parts.joinToString(", ")
    }
}
