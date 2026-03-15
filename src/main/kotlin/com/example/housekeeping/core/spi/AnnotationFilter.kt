package com.example.housekeeping.core.spi

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.uast.UAnnotated

interface AnnotationFilter {

    fun shouldKeep(element: UAnnotated): Boolean

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName<AnnotationFilter>("com.nexxlabs.housekeeping.annotationFilter")
    }
}
