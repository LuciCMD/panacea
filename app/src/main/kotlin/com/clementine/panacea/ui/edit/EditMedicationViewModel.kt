package com.clementine.panacea.ui.edit

import androidx.compose.runtime.saveable.Saver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clementine.panacea.PanaceaApp
import com.clementine.panacea.data.DoseChange
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.PhotoStore
import com.clementine.panacea.data.db.MedicationCounts
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The add / edit screen's link to the data. It holds no form state (that lives in the screen and
 * survives rotation there), so one instance serves every visit.
 */
class EditMedicationViewModel(private val repository: MedicationRepository, val photos: PhotoStore) : ViewModel() {

    suspend fun load(id: Long): MedicationDraft? = repository.medication(id)?.let { (med, ingredients) -> Drafts.from(med, ingredients) }

    suspend fun check(draft: MedicationDraft): DraftProblems = Drafts.check(draft, repository.names())

    suspend fun counts(id: Long): MedicationCounts = repository.counts(id)

    /** What to ask before saving [draft], if it changes the dose, weight or ingredients that past doses were logged at. */
    suspend fun pastDosesQuestion(draft: MedicationDraft): String? {
        if (draft.isNew) return null
        val (existing, ingredients) = repository.medication(draft.id) ?: return null
        val change = DoseChange(existing, Drafts.toEntity(draft, existing, sortOrder = 0), ingredients, Drafts.toIngredients(draft, draft.id))
        if (!change.dose && !change.weight && !change.ingredients) return null
        val count = repository.dosesAffected(draft.id, change)
        return if (count == 0) null else Drafts.pastDosesQuestion(change, count, existing)
    }

    /** Saves [draft] and tidies up photos it replaced; with [fixPast], past doses take its new dose and weight. */
    fun save(draft: MedicationDraft, fixPast: Boolean = false, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = if (draft.isNew) null else repository.medication(draft.id)?.first
            val (id, previous) = repository.save(Drafts.toEntity(draft, existing, sortOrder = 0), fixPast) { Drafts.toIngredients(draft, it) }
            listOfNotNull(previous?.photoFront, previous?.photoBack)
                .filter { it != draft.photoFront && it != draft.photoBack }
                .forEach(photos::delete)
            onSaved(id)
        }
    }

    fun delete(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)?.let {
                photos.delete(it.photoFront)
                photos.delete(it.photoBack)
            }
            onDeleted()
        }
    }

    /** Leaving without saving: drop photos taken for this draft that nothing else uses. */
    fun discardPhotos(draft: MedicationDraft, original: MedicationDraft?) {
        listOfNotNull(draft.photoFront, draft.photoBack)
            .filter { it != original?.photoFront && it != original?.photoBack }
            .forEach(photos::delete)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PanaceaApp).container
                EditMedicationViewModel(container.medications, container.photos)
            }
        }

        /** Keeps a draft through rotation and process death. */
        val DraftSaver: Saver<MedicationDraft?, String> = Saver(
            save = { d ->
                d?.let {
                    JSONObject()
                        .put("id", it.id).put("name", it.name)
                        .put("dose", it.dose).put("doseUnit", it.doseUnit)
                        .put("weight", it.weight).put("weightUnit", it.weightUnit)
                        .put("category", it.category.key).put("type", it.type.key)
                        .put("front", it.photoFront ?: "").put("back", it.photoBack ?: "")
                        .put("ingredients", JSONArray(it.ingredients.map { i -> JSONArray(listOf(i.name, i.amount, i.unit)) }))
                        .toString()
                } ?: ""
            },
            restore = { text ->
                if (text.isEmpty()) {
                    null
                } else {
                    val o = JSONObject(text)
                    val list = o.getJSONArray("ingredients")
                    MedicationDraft(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        dose = o.getString("dose"),
                        doseUnit = o.getString("doseUnit"),
                        weight = o.getString("weight"),
                        weightUnit = o.getString("weightUnit"),
                        category = Category.fromKey(o.getString("category")),
                        type = MedicationType.fromKey(o.getString("type")),
                        ingredients = (0 until list.length()).map { i ->
                            val row = list.getJSONArray(i)
                            IngredientDraft(row.getString(0), row.getString(1), row.getString(2))
                        },
                        photoFront = o.getString("front").ifEmpty { null },
                        photoBack = o.getString("back").ifEmpty { null },
                    )
                }
            },
        )
    }
}
