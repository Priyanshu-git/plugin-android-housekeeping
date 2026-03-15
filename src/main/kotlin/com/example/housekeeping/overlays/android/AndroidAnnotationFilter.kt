package com.example.housekeeping.overlays.android

import com.example.housekeeping.core.spi.AnnotationFilter
import org.jetbrains.uast.UAnnotated

class AndroidAnnotationFilter : AnnotationFilter {

    companion object {
        private val ANDROID_KEEP_ANNOTATIONS = setOf(
            "OnClick", "OnTouch", "BindingAdapter"
        )
    }

    override fun shouldKeep(element: UAnnotated): Boolean {
        return element.uAnnotations.any { uAnn ->
            val name = uAnn.qualifiedName?.substringAfterLast(".")
                ?: uAnn.uastAnchor?.sourcePsi?.text?.trimStart('@')
            name != null && ANDROID_KEEP_ANNOTATIONS.any { k -> name.contains(k) }
        }
    }
}
