package com.clementine.panacea.ui.medication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import com.clementine.panacea.data.catalog.Chemistry
import com.clementine.panacea.model.formatAmount
import com.clementine.panacea.ui.components.Numbers
import com.clementine.panacea.ui.components.SectionCard
import com.clementine.panacea.ui.theme.Colors
import kotlin.math.hypot

/** Past this many atoms a drawing is a tangle at phone size, so only the formula shows */
private const val MAX_DRAWN_ATOMS = 90

/** What each active is, from PubChem: its structure, formula, molar mass and systematic name */
@Composable
fun ChemistryCard(compounds: List<Chemistry>) {
    SectionCard(
        "Chemistry",
        info = "Each active's structure and formula, as listed for it in PubChem, the US National Library of Medicine's " +
            "chemistry database. The drawing leaves out the hydrogens on carbon, as chemists do.",
    ) {
        compounds.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Colors.LineSoft)
            Compound(c, showTitle = compounds.size > 1)
        }
    }
}

@Composable
private fun Compound(c: Chemistry, showTitle: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTitle) Text(c.title, style = MaterialTheme.typography.titleSmall, color = Colors.Ink)
        if (c.atoms.isNotEmpty() && c.atoms.count { it.element != 1 } <= MAX_DRAWN_ATOMS) {
            Structure(c, Modifier.fillMaxWidth().semantics { contentDescription = "Structure of ${c.title}" })
        }
        Fact("Formula", formula(c.formula))
        if (c.weight > 0) Fact("Molar Mass", AnnotatedString("${formatAmount(c.weight)} g/mol"))
        c.iupac?.let { Fact("IUPAC Name", AnnotatedString(it)) }
        Text("PubChem CID ${c.cid}", style = MaterialTheme.typography.bodySmall.merge(Numbers), color = Colors.Muted)
    }
}

@Composable
private fun Fact(label: String, value: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Colors.Muted, modifier = Modifier.width(96.dp))
        // Long systematic names are worth copying into a search
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Colors.Ink)
        }
    }
}

/** "C13H18O2" with its counts set as subscripts */
fun formula(text: String): AnnotatedString = buildAnnotatedString {
    text.forEach { ch ->
        if (ch.isDigit()) withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)) { append(ch) } else append(ch)
    }
}

/**
 * A skeletal drawing from PubChem's 2D coordinates: carbons are corners, other atoms are labelled
 * with the hydrogens on them, double and triple bonds are drawn as two and three lines
 */
@Composable
private fun Structure(c: Chemistry, modifier: Modifier, maxHeight: Dp = 200.dp) {
    val heavy = c.atoms.indices.filter { c.atoms[it].element != 1 }.toSet()
    val hydrogens = IntArray(c.atoms.size) { c.atoms[it].hydrogens }
    val degree = IntArray(c.atoms.size)
    c.bonds.forEach { b ->
        if (c.atoms[b.a].element == 1 && b.b in heavy) hydrogens[b.b]++
        if (c.atoms[b.b].element == 1 && b.a in heavy) hydrogens[b.a]++
        if (b.a in heavy && b.b in heavy) {
            degree[b.a]++
            degree[b.b]++
        }
    }
    val xs = heavy.map { c.atoms[it].x }
    val ys = heavy.map { c.atoms[it].y }
    val spanX = ((xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)).coerceAtLeast(1f)
    val spanY = ((ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)).coerceAtLeast(0.5f)
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.bodyMedium
    val (ink, hetero) = Colors.Ink to Colors.Accent

    BoxWithConstraints(modifier) {
        // Bonds about 30dp long at most, so a small molecule isn't blown up to fill the card
        val unit = minOf(maxWidth / (spanX + 1f), maxHeight / (spanY + 1f), 30.dp)
        val height = min(maxHeight, unit * (spanY + 1f))
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val u = unit.toPx()
            val ox = (size.width - spanX * u) / 2 - (xs.minOrNull() ?: 0f) * u
            // PubChem's y runs up, the screen's down
            val oy = (size.height - spanY * u) / 2 + (ys.maxOrNull() ?: 0f) * u
            fun at(i: Int) = Offset(ox + c.atoms[i].x * u, oy - c.atoms[i].y * u)
            val labelled = { i: Int -> c.atoms[i].element != 6 || degree[i] == 0 }
            val gap = 8.dp.toPx()
            val stroke = 1.4.dp.toPx()

            c.bonds.forEach { b ->
                if (b.a !in heavy || b.b !in heavy) return@forEach
                var p = at(b.a)
                var q = at(b.b)
                val len = hypot(q.x - p.x, q.y - p.y).coerceAtLeast(1f)
                val dir = Offset((q.x - p.x) / len, (q.y - p.y) / len)
                if (labelled(b.a)) p += dir * gap
                if (labelled(b.b)) q -= dir * gap
                val normal = Offset(-dir.y, dir.x) * 2.6.dp.toPx()
                val offsets = when (b.order) {
                    2 -> listOf(normal * 0.5f, normal * -0.5f)
                    3 -> listOf(normal, Offset.Zero, -normal)
                    else -> listOf(Offset.Zero)
                }
                offsets.forEach { o -> drawLine(ink.copy(alpha = 0.85f), p + o, q + o, stroke, StrokeCap.Round) }
            }
            heavy.filter(labelled).forEach { i ->
                val symbol = SYMBOLS.getOrElse(c.atoms[i].element) { "?" }
                val h = hydrogens[i]
                val text = buildAnnotatedString {
                    append(symbol)
                    if (h > 0) append("H")
                    if (h > 1) withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)) { append(h.toString()) }
                }
                val laid = measurer.measure(text, label.copy(color = if (c.atoms[i].element == 6) ink else hetero))
                drawText(laid, topLeft = at(i) - Offset(laid.size.width / 2f, laid.size.height / 2f))
            }
        }
    }
}

/** Element symbols by atomic number, as PubChem numbers them */
private val SYMBOLS = listOf(
    "", "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca",
    "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr",
    "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd",
    "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
    "Tl", "Pb", "Bi", "Po", "At", "Rn",
)
