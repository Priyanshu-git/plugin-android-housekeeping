package com.example.housekeeping.core.spi

import com.intellij.openapi.project.Project

data class AnalysisContext(
    val project: Project,
    val entryPointProviders: List<EntryPointProvider>,
    val annotationFilters: List<AnnotationFilter>,
    val syntheticFilters: List<SyntheticFilter>
)
