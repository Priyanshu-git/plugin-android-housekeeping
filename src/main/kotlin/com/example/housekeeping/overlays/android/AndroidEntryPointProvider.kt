package com.example.housekeeping.overlays.android

import com.example.housekeeping.core.spi.EntryPointProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil

class AndroidEntryPointProvider : EntryPointProvider {

    companion object {
        private val ANDROID_ENTRY_POINTS = setOf(
            "android.app.Activity", "androidx.fragment.app.Fragment",
            "android.app.Service", "android.content.BroadcastReceiver",
            "android.content.ContentProvider", "android.app.Application",
            "android.view.View", "android.view.ViewModel", "androidx.lifecycle.ViewModel"
        )
    }

    override fun isApplicable(project: Project): Boolean = true

    override fun isEntryPoint(psiClass: PsiClass): Boolean {
        return ANDROID_ENTRY_POINTS.any { baseFqn ->
            InheritanceUtil.isInheritor(psiClass, baseFqn)
        }
    }
}
