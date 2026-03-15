package com.example.housekeeping.core.model

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

data class UnusedItem(
    val elementPointer: SmartPsiElementPointer<PsiElement>,
    val name: String,
    val path: String,
    val type: ItemType,
    val reason: String
) {
    val element: PsiElement? get() = elementPointer.element

    override fun toString(): String = name
}
