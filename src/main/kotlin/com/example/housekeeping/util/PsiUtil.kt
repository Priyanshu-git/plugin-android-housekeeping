package com.example.housekeeping.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPackage
import com.intellij.psi.util.PsiTreeUtil

object PsiUtil {

    fun collectClasses(e: AnActionEvent): List<PsiClass>? {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val file = e.getData(CommonDataKeys.PSI_FILE)

        return when {
            element is PsiClass -> listOf(element)
            element is PsiPackage -> element.classes.toList()
            file is PsiJavaFile -> file.classes.toList()
            file != null -> PsiTreeUtil.collectElementsOfType(file, PsiClass::class.java).toList()
            else -> null
        }
    }
}
