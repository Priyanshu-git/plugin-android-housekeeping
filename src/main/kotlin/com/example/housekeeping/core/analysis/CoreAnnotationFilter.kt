package com.example.housekeeping.core.analysis

import com.example.housekeeping.core.spi.AnnotationFilter
import org.jetbrains.uast.UAnnotated

class CoreAnnotationFilter : AnnotationFilter {

    companion object {
        private val CORE_KEEP_ANNOTATIONS = setOf(
            "Keep", "Inject", "Provides", "GET", "POST"
        )
    }

    override fun shouldKeep(element: UAnnotated): Boolean {
        return element.uAnnotations.any { uAnn ->
            val name = uAnn.qualifiedName?.substringAfterLast(".")
                ?: uAnn.uastAnchor?.sourcePsi?.text?.trimStart('@')
            name != null && CORE_KEEP_ANNOTATIONS.any { k -> name.contains(k) }
        }
    }
}
