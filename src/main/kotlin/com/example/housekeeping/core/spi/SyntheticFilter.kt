package com.example.housekeeping.core.spi

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

interface SyntheticFilter {

    fun isSyntheticMethod(method: UMethod): Boolean

    fun isSyntheticClass(uClass: UClass): Boolean

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName<SyntheticFilter>("com.nexxlabs.housekeeping.syntheticFilter")
    }
}
