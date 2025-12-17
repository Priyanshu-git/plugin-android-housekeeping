package com.example.housekeeping

import com.intellij.psi.PsiElement

enum class ItemType {
    METHOD, CLASS, RESOURCE, OTHER
}

data class UnusedItem(
    val element: PsiElement,
    val name: String,
    val path: String,
    val type: ItemType,
    val reason: String
) {
    override fun toString(): String = name
}