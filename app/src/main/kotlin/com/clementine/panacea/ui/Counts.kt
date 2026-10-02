package com.clementine.panacea.ui

import java.text.NumberFormat
import java.util.Locale

/** "1 dose", "4,145 doses": the number grouped the phone's way, the noun matching it. */
fun counted(n: Int, noun: String, plural: String = noun + "s", locale: Locale = Locale.getDefault()): String =
    "${NumberFormat.getIntegerInstance(locale).format(n)} ${if (n == 1) noun else plural}"
