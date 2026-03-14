# Project Overview

## Purpose

Housekeeping is an on-demand IntelliJ/Android Studio plugin that scans a user-selected scope (file, directory, or multi-selection) for code and resources that are not referenced anywhere in the project, then lets the developer safely delete them through IntelliJ's built-in Safe Delete with undo support.

The core problem it addresses: Android projects accumulate dead code and orphaned XML resources over time. IDE inspections catch some of these, but they are coarse-grained and project-wide. Housekeeping gives developers targeted, scoped analysis on demand — right-click exactly what you want inspected, review a checklist of candidates, and delete only what you choose.

## Key Features

| Feature | Detail |
|---|---|
| **Three analysis modes** | Unused Methods, Unused Classes, Unused XML Resources |
| **Scoped analysis** | Works on a single file, a directory tree, or a multi-file selection |
| **UAST-based parsing** | Language-agnostic; handles Kotlin and Java transparently |
| **False-positive suppression** | Skips Android entry points, annotated members, overrides, and compiler-generated methods |
| **Two-pass resource search** | Validates both `R.type.name` (code) and `@type/name` (XML) patterns to avoid misses |
| **Safe Delete integration** | Deletions go through IntelliJ's conflict-resolution dialog; fully undoable |
| **Cancellable background task** | Progress indicator with per-file granularity; cancel at any time |
| **Icon-rich result list** | Inline platform icons (class/method/resource) next to each checklist item |
| **Double-click navigation** | Opens the source file and jumps to the declaration directly from the result list |

## Problems It Solves

- Orphaned utility methods that were never called after a refactor.
- Top-level classes that became dead code when a feature was removed.
- XML layouts, drawables, menus, and value resources (strings, colors, dimens, styles, etc.) that are no longer referenced in code or other XML files.

## High-Level Architecture

```
User right-clicks scope
        │
        ▼
BaseHousekeepingAction       ← validates scope, guards against indexing
        │
        ▼
HousekeepingAnalyzer         ← background thread; per-file ReadAction blocks
        │
        ├── analyzeUFile()   ← UAST traversal for Kotlin/Java
        ├── analyzeMethod()  ← ReferencesSearch + OverridingMethodsSearch
        ├── analyzeClass()   ← ReferencesSearch + InheritanceUtil
        └── analyzeResourceFile() ← PsiSearchHelper two-pass word search
        │
        ▼
List<UnusedItem>             ← SmartPsiElementPointers (safe after PSI changes)
        │
        ▼
HousekeepingToolWindowPanel  ← EDT; IconCheckBoxList + details pane
        │
        ▼
DeleteHandler.deletePsiElement()  ← Safe Delete with undo
```

## Limitations

- **No cross-module analysis.** The search scope is `GlobalSearchScope.projectScope()`, which covers all modules in the open project but not external libraries.
- **Reflection and runtime references are invisible.** A method called only via `Method.invoke()` or a resource referenced only from a dynamically constructed string will be reported as unused.
- **Resource analysis covers layout, drawable, anim, animator, menu, raw, xml, mipmap folders and the value types string, color, dimen, style, integer, bool, id.** Other resource categories are not inspected.
- **Not DumbAware.** Analysis cannot run while IntelliJ is indexing; attempting to do so shows an informational notification and aborts.
- **No persistent configuration.** There is no settings page; all parameters are hard-coded in the analyzer.
