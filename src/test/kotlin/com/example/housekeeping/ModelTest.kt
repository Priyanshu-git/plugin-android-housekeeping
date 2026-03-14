package com.example.housekeeping

import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModelTest : BasePlatformTestCase() {

    // --- AnalysisMode ---

    fun testAnalysisModeHasThreeValues() {
        assertEquals(3, AnalysisMode.values().size)
    }

    fun testMethodsDisplayName() {
        assertEquals("Methods", AnalysisMode.METHODS.displayName)
    }

    fun testClassesDisplayName() {
        assertEquals("Classes", AnalysisMode.CLASSES.displayName)
    }

    fun testResourcesDisplayName() {
        assertEquals("Resources", AnalysisMode.RESOURCES.displayName)
    }

    // --- ItemType ---

    fun testItemTypeHasFourValues() {
        assertEquals(4, ItemType.values().size)
    }

    fun testItemTypeContainsExpectedValues() {
        ItemType.valueOf("METHOD")
        ItemType.valueOf("CLASS")
        ItemType.valueOf("RESOURCE")
        ItemType.valueOf("OTHER")
    }

    // --- UnusedItem ---

    fun testToStringReturnsName() {
        val psiFile = myFixture.configureByText("Test.kt", "class Test")
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiFile as com.intellij.psi.PsiElement)
        val item = UnusedItem(pointer, "myMethod()", "/path/Test.kt", ItemType.METHOD, "No references")
        assertEquals("myMethod()", item.toString())
    }

    fun testElementResolvesFromSmartPointer() {
        val psiFile = myFixture.configureByText("Test.kt", "class Test")
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiFile as com.intellij.psi.PsiElement)
        val item = UnusedItem(pointer, "Test", "/path/Test.kt", ItemType.CLASS, "Unused")
        assertNotNull("Element should resolve from smart pointer", item.element)
        assertTrue("Resolved element should be valid", item.element!!.isValid)
    }

    fun testFieldsPreserved() {
        val psiFile = myFixture.configureByText("Test.kt", "class Test")
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiFile as com.intellij.psi.PsiElement)
        val item = UnusedItem(pointer, "res/icon", "/res/drawable/icon.xml", ItemType.RESOURCE, "Not used")
        assertEquals("res/icon", item.name)
        assertEquals("/res/drawable/icon.xml", item.path)
        assertEquals(ItemType.RESOURCE, item.type)
        assertEquals("Not used", item.reason)
    }

    fun testDataClassEquality() {
        val psiFile = myFixture.configureByText("Test.kt", "class Test")
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiFile as com.intellij.psi.PsiElement)
        val item1 = UnusedItem(pointer, "name", "path", ItemType.METHOD, "reason")
        val item2 = UnusedItem(pointer, "name", "path", ItemType.METHOD, "reason")
        assertEquals(item1, item2)
    }
}
