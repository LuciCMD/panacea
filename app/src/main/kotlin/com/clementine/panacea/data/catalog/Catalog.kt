package com.clementine.panacea.data.catalog

import android.content.Context
import android.util.Log
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.util.zip.GZIPInputStream

enum class CatalogKind { GENERIC, BRAND, OTHER }

data class OtherActive(val name: String, val amount: Double, val unit: String)

/** One way a medication comes: "200 mg tablet, OTC"; [per] is a liquid's volume, as in "100 mg per 5 mL" */
data class CatalogForm(
    val type: MedicationType,
    val category: Category,
    val dose: Double?,
    val unit: String?,
    val per: String?,
    val others: List<OtherActive>,
)

/** [index] is its line in the asset, which is how a route names it; stable within one build of the app */
data class CatalogEntry(
    val index: Int,
    val kind: CatalogKind,
    val name: String,
    val aliases: List<String>,
    val ingredients: List<String>,
    val cids: List<Int>,
    val forms: List<CatalogForm>,
)

/** [hydrogens] attached and left implicit, as RDKit gives them; PubChem lists hydrogens as atoms instead */
data class Atom(val element: Int, val x: Float, val y: Float, val hydrogens: Int = 0)
data class Bond(val a: Int, val b: Int, val order: Int)

/** A compound as PubChem has it; [title] is PubChem's own name for it */
data class Chemistry(
    val cid: Int,
    val title: String,
    val formula: String,
    val weight: Double,
    val iupac: String?,
    val atoms: List<Atom>,
    val bonds: List<Bond>,
)

data class CatalogHit(val entry: CatalogEntry, val alias: String?)

/**
 * The medications to search when adding one, and their chemistry; built from public US and WHO
 * lists and PubChem by tools/catalog, and read from the app's assets, so nothing is looked up online
 */
class Catalog(private val context: Context) {
    private val lock = Mutex()
    private var entries: List<CatalogEntry>? = null
    private var keys: List<List<String>> = emptyList()
    private var chemistry: Map<Int, Chemistry>? = null

    suspend fun entries(): List<CatalogEntry> = lock.withLock {
        entries ?: withContext(Dispatchers.IO) { readEntries() }.also { list ->
            entries = list
            keys = list.map { e -> (listOf(e.name) + e.aliases).map(::fold) }
        }
    }

    suspend fun entry(index: Int): CatalogEntry? = entries().getOrNull(index)

    /**
     * Best first: a whole name, then a name that starts with it, then a word in a name that does,
     * then anywhere in it; within each, the more widely sold first, as the asset is ordered
     */
    suspend fun search(query: String, limit: Int = 60): List<CatalogHit> {
        val list = entries()
        val q = fold(query)
        if (q.length < 2) return emptyList()
        val ranked = ArrayList<Triple<Int, Int, CatalogHit>>()
        list.forEachIndexed { i, e ->
            var best = Int.MAX_VALUE
            var via: String? = null
            keys[i].forEachIndexed { k, key ->
                val rank = when {
                    key == q -> 0
                    key.startsWith(q) -> 1
                    key.split(' ', '-', '/').any { it.startsWith(q) } -> 2
                    key.contains(q) -> 3
                    else -> return@forEachIndexed
                } + if (k > 0) 1 else 0
                if (rank < best) {
                    best = rank
                    via = if (k > 0) e.aliases[k - 1] else null
                }
            }
            // Below every name match: brands of what was typed, so "ibuprofen" finds Advil too
            if (best == Int.MAX_VALUE && e.kind == CatalogKind.BRAND && e.ingredients.any { fold(it).startsWith(q) }) best = 5
            if (best != Int.MAX_VALUE) ranked += Triple(best, i, CatalogHit(e, via))
        }
        return ranked.sortedWith(compareBy({ it.first }, { it.second })).take(limit).map { it.third }
    }

    /**
     * The compounds in a medication, found by its own name and its ingredients' names, so ones
     * added before the catalog existed have them too
     */
    suspend fun compoundsOf(names: List<String>): List<Chemistry> {
        val list = entries()
        val chem = chemistry()
        // Each name's compound, and what to call it: a brand by its main drug, anything else as the user wrote it
        val byName = HashMap<String, Pair<Int, String?>>()
        list.forEach { e ->
            e.cids.firstOrNull()?.takeIf { it > 0 }?.let { cid ->
                val called = if (e.kind == CatalogKind.BRAND) e.ingredients.firstOrNull() else null
                (listOf(e.name) + e.aliases).forEach { byName.putIfAbsent(fold(it), cid to called) }
            }
            e.ingredients.zip(e.cids).forEach { (n, cid) -> if (cid > 0) byName.putIfAbsent(fold(n), cid to null) }
        }
        return names.mapNotNull { n -> byName[fold(n)]?.let { (cid, called) -> cid to (called ?: n) } }
            .distinctBy { it.first }
            .mapNotNull { (cid, called) -> chem[cid]?.copy(title = called) }
    }

    private suspend fun chemistry(): Map<Int, Chemistry> = lock.withLock {
        chemistry ?: withContext(Dispatchers.IO) { readChemistry() }.also { chemistry = it }
    }

    // A build without the catalog still runs, just without search results or chemistry
    private fun lines(asset: String): Sequence<String> = try {
        GZIPInputStream(context.assets.open(asset)).bufferedReader().let(BufferedReader::lineSequence)
    } catch (e: java.io.IOException) {
        Log.w("Panacea", "No catalog asset $asset", e)
        emptySequence()
    }

    private fun readEntries(): List<CatalogEntry> = lines("catalog/medications.gzjsonl").mapIndexed { i, line ->
        val o = JSONObject(line)
        CatalogEntry(
            index = i,
            kind = when (o.getString("kind")) {
                "brand" -> CatalogKind.BRAND
                "generic" -> CatalogKind.GENERIC
                else -> CatalogKind.OTHER
            },
            name = o.getString("name"),
            aliases = o.optJSONArray("aliases").strings(),
            ingredients = o.optJSONArray("ingredients").strings(),
            cids = o.optJSONArray("cids").let { a -> List(a?.length() ?: 0) { a!!.getInt(it) } },
            forms = o.optJSONArray("forms").objects().map { f ->
                CatalogForm(
                    type = MedicationType.fromKey(f.getString("type")),
                    category = Category.fromKey(f.optString("category")),
                    dose = f.optDouble("dose").takeIf { !it.isNaN() && it > 0 },
                    unit = f.optString("unit").ifEmpty { null },
                    per = f.optString("per").ifEmpty { null },
                    others = f.optJSONArray("others").objects().map { OtherActive(it.getString("name"), it.getDouble("amount"), it.getString("unit")) },
                )
            },
        )
    }.toList()

    private fun readChemistry(): Map<Int, Chemistry> = lines("catalog/chemistry.gzjsonl").map { line ->
        val o = JSONObject(line)
        Chemistry(
            cid = o.getInt("cid"),
            title = if (o.isNull("title")) "" else o.optString("title"),
            formula = o.getString("formula"),
            weight = o.optDouble("weight", 0.0),
            iupac = if (o.isNull("iupac")) null else o.optString("iupac").ifEmpty { null },
            atoms = o.optJSONArray("atoms").arrays().map { Atom(it.getInt(0), it.getDouble(1).toFloat(), it.getDouble(2).toFloat(), it.optInt(3, 0)) },
            bonds = o.optJSONArray("bonds").arrays().map { Bond(it.getInt(0), it.getInt(1), it.getInt(2)) },
        )
    }.associateBy { it.cid }

    companion object {
        /** For matching: case, accents and punctuation don't count */
        fun fold(s: String): String =
            java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9 /-]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
private fun JSONArray?.arrays(): List<JSONArray> = if (this == null) emptyList() else List(length()) { getJSONArray(it) }
