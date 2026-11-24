package dev.beadbelt.game

/** Тексты уроков: карточка с объяснением, задание на уровне, похвала и приз. Лезут в окно «i»: до ~7 строк по ~35 знаков. */
object LessonText {
    fun title(ls: Lesson) = when (ls.id) {
        LS_BASICS -> tr("Как играть", "How to play")
        LS_BULB -> tr("Лампочка-подсказка", "The hint bulb")
        LS_ROCKET -> tr("Ракета", "The rocket")
        LS_RETURN -> tr("Кнопка «Вернуть»", "The Return button")
        LS_LINKS -> tr("Связанные лотки", "Linked trays")
        else -> tr("Коробка с замком", "Padlocked box")
    }

    fun body(ls: Lesson) = when (ls.id) {
        LS_BASICS -> tr(
            "Нажми на лоток вверху: шарики высыплются в воронку и поедут по конвейеру. Когда шарик проезжает мимо коробки своего цвета, он залетает внутрь. В коробке три места. Собери все коробки!",
            "Tap a tray at the top: its beads pour into the funnel and ride the belt. When a bead passes a box of its own color, it jumps inside. A box holds three. Fill every box!")
        LS_BULB -> tr(
            "Застрял? Нажми лампочку внизу: она просчитает партию и покажет лоток, который ведёт к победе. Подсказки влияют на звёзды: без помощи их три. Сейчас лампочка бесплатная, попробуй. За уровень получишь три подсказки.",
            "Stuck? Tap the bulb below: it analyzes the position and points to the tray that leads to a win. Hints affect stars: with no help you get three. The bulb is free now, try it. Finish the level to get three hints.")
        LS_ROCKET -> tr(
            "Ракета мгновенно закрывает самую выгодную коробку и убирает нужные шарики с поля. Бери её, когда лента почти забита. Сейчас ракета бесплатная, попробуй. За уровень получишь две ракеты.",
            "The rocket instantly fills the most useful box and removes the beads it needs from the board. Use it when the belt is nearly full. It is free now, try it. Finish the level to get two rockets.")
        LS_RETURN -> tr(
            "Конвейер вмещает ограниченное число шариков. Забьётся и ни одна коробка не подойдёт, значит проигрыш. «Вернуть» снимает до 8 шариков с ленты и возвращает их в лотки. Сейчас бесплатно, за уровень получишь ещё две.",
            "The belt holds a limited number of beads. If it jams and no box matches, you lose. Return takes up to 8 beads off the belt and puts them back into trays. Free now; finish the level to get two more.")
        LS_LINKS -> tr(
            "Лотки в золотой рамке связаны. Нажми на ведущий лоток (от него идёт стрелка), и ведомый высыплется вместе с ним. Нажмёшь на ведомый, высыплется только он: назад связь не работает.",
            "Trays in a golden frame are linked. Tap the leading tray (the arrow starts there) and the follower pours out with it. Tap the follower and only it pours: the link never works backwards.")
        else -> tr(
            "Коробка с замком пока не принимает шарики. Собери любую другую коробку, и замок откроется. Выбирай, что собирать первым.",
            "A padlocked box does not accept beads yet. Fill any other box and the lock opens. Choose what to fill first.")
    }

    fun ok(ls: Lesson) = if (ls.id == LS_BASICS) tr("Поехали", "Let's go") else tr("Попробовать", "Try it")
    fun tag() = tr("Новое", "New")

    /** Задание, которое висит на уровне, пока игрок не сделает нужное. */
    fun task(ls: Lesson) = when (ls.id) {
        LS_BASICS -> tr("Нажми на подсвеченный лоток", "Tap the highlighted tray")
        LS_BULB -> tr("Нажми на лампочку внизу", "Tap the bulb below")
        LS_ROCKET -> tr("Нажми на ракету внизу", "Tap the rocket below")
        LS_RETURN -> tr("Когда шарики поедут по ленте, нажми «Вернуть»", "Once beads ride the belt, tap Return")
        LS_LINKS -> tr("Нажми на лоток, от которого идёт стрелка", "Tap the tray the arrow starts from")
        else -> tr("Собери любую коробку: замок откроется", "Fill any box: the lock opens")
    }

    fun praise(ls: Lesson) = when (ls.id) {
        LS_BASICS -> tr("Отлично! Теперь собери все коробки", "Great! Now fill every box")
        LS_BULB -> tr("Вот так! Теперь доведи уровень до конца", "That's it! Now finish the level")
        LS_ROCKET -> tr("Бум! Коробка закрыта. Заверши уровень", "Boom! Box filled. Finish the level")
        LS_RETURN -> tr("Шарики вернулись в лотки. Заверши уровень", "Beads are back in trays. Finish the level")
        LS_LINKS -> tr("Оба лотка высыпались разом! Заверши уровень", "Both trays poured at once! Finish the level")
        else -> tr("Замок открыт! Заверши уровень", "Lock opened! Finish the level")
    }

    /** Строка приза для итоговой карточки. */
    fun prize(ls: Lesson): String {
        val p = ls.prize
        val parts = ArrayList<String>(4)
        if (p.coins > 0) parts.add(tr("+${p.coins} монет", "+${p.coins} coins"))
        if (p.rockets > 0) parts.add(tr("ракета ×${p.rockets}", "rocket ×${p.rockets}"))
        if (p.returns > 0) parts.add(tr("«Вернуть» ×${p.returns}", "Return ×${p.returns}"))
        if (p.hints > 0) parts.add(tr("подсказка ×${p.hints}", "hint ×${p.hints}"))
        return tr("Приз: ", "Prize: ") + parts.joinToString(", ")
    }
}
