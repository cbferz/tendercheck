package pro.tendercheck.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты форматирования чисел, сумм и времени. */
class FormatTest {
    @Test
    fun formatsDecimalsWithCommaAndTrueMinus() {
        assertEquals("1,25", formatDecimal(1.25, 2))
        assertEquals("9,5", formatDecimal(9.5, 1))
        assertEquals("−2,0", formatDecimal(-2.0, 1))
        assertEquals("0,0", formatDecimal(0.0, 1))
        assertEquals("40", formatDecimal(40.0, 0))
    }

    @Test
    fun formatsMoney() {
        assertEquals("40 млн ₽", formatMoneyMln(40.0))
        assertEquals("7,5 млн ₽", formatMoneyMln(7.5))
        assertEquals("1,2 млрд ₽", formatMoneyMln(1200.0))
    }

    @Test
    fun formatsDuration() {
        assertEquals("1:15", formatDuration(75))
        assertEquals("0:05", formatDuration(5))
        assertEquals("0:00", formatDuration(-3))
    }

    @Test
    fun choosesRussianPluralForm() {
        fun word(n: Int) = pluralRu(n, "заявка", "заявки", "заявок")
        assertEquals("заявка", word(1))
        assertEquals("заявки", word(2))
        assertEquals("заявок", word(5))
        assertEquals("заявок", word(11))
        assertEquals("заявка", word(21))
        assertEquals("заявки", word(24))
    }
}
