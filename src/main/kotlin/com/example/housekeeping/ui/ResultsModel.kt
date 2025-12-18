package com.example.housekeeping.ui

import com.example.housekeeping.analysis.AnalysisResult
import javax.swing.AbstractListModel

class ResultsModel : AbstractListModel<AnalysisResult>() {

    private val items = mutableListOf<AnalysisResult>()

    fun setResults(results: List<AnalysisResult>) {
        items.clear()
        items.addAll(results)
        fireContentsChanged(this, 0, items.size)
    }

    override fun getSize(): Int = items.size
    override fun getElementAt(index: Int): AnalysisResult = items[index]
}
