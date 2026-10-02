package com.clementine.panacea.data

import com.clementine.panacea.data.db.MedicationSummary
import com.clementine.panacea.data.db.PanaceaDatabase
import kotlinx.coroutines.flow.Flow

class MedicationRepository(private val db: PanaceaDatabase) {
    fun observeSummaries(): Flow<List<MedicationSummary>> = db.medicationDao().observeSummaries()
}
