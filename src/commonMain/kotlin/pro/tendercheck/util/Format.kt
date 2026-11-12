package pro.tendercheck.util

import kotlin.math.abs
import kotlin.math.round

/** Знак минуса для отображения отрицательных чисел. */
private const val MINUS = "−"

/**
 * Форматирует число с заданным числом знаков после запятой, десятичный разделитель — запятая.
 * Реализация не зависит от платформы (в общем коде Kotlin нет String.format).
 */
fun formatDecimal(value: Double, decimals: Int = 1): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(abs(value) * factor).toLong()
    val intPart = scaled / factor
    val sign = if (value < 0 && scaled != 0L) MINUS else ""
    if (decimals == 0) return "$sign$intPart"
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$intPart,$fraction"
}

/** Сумма в млн руб.: «45 млн ₽», «7,5 млн ₽», «1,2 млрд ₽». */
fun formatMoneyMln(amountMln: Double): String {
    if (amountMln >= 1000.0) return formatDecimal(amountMln / 1000.0, 1) + " млрд ₽"
    val whole = amountMln == round(amountMln)
    val text = if (whole) formatDecimal(amountMln, 0) else formatDecimal(amountMln, 1)
    return "$text млн ₽"
}

/** Длительность в секундах в виде «м:сс». */
fun formatDuration(totalSec: Int): String {
    val sec = if (totalSec < 0) 0 else totalSec
    return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
}

/** Выбор формы существительного по числу: 1 заявка, 2 заявки, 5 заявок. */
fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val m100 = abs(n) % 100
    val m10 = abs(n) % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1 -> one
        m10 in 2..4 -> few
        else -> many
    }
}
