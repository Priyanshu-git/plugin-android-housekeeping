# Development and Maintenance

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17 (enforced by Kotlin and Java toolchains in `build.gradle.kts`) |
| Gradle | 8.x (via wrapper — `./gradlew`) |
| IntelliJ Platform SDK | 2024.2.1 IC (downloaded automatically by the Gradle IntelliJ plugin) |

Set `org.gradle.java.home` in `gradle.properties` (or your system `JAVA_HOME`) to point to a JDK 17 installation. The file already contains a Windows default path; update it for your environment.

## Build Commands

```bash
# Build distributable ZIP → build/distributions/housekeeping-plugin-1.0.0.zip
./gradlew buildPlugin

# Launch a sandboxed IntelliJ instance with the plugin loaded (hot development loop)
./gradlew runIde

# Full build including compilation and verification
./gradlew build

# Run unit tests (requires ideaIC-2024.2.1 in Gradle cache — downloaded on first run)
./gradlew test
```

## Running Tests

Tests use `BasePlatformTestCase` from the IntelliJ test framework. The test task auto-discovers the IDE distribution in the Gradle module cache and sets `idea.home.path` accordingly.

On first run, Gradle downloads `ideaIC-2024.2.1` (~700 MB). Subsequent runs reuse the cache. If the cache location is non-standard, update the path in `build.gradle.kts`:

```kotlin
val cacheBase = file("${gradle.gradleUserHomeDir}/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2024.2.1")
```

The test sandbox setup task copies the bundled `java` and `Kotlin` plugins from the IDE distribution so their UAST extension points are registered during test execution.

Current tests (`ModelTest.kt`) cover the data model only:
- `AnalysisMode` values and display names
- `ItemType` values
- `UnusedItem` field access, `toString()`, equality, and `SmartPsiElementPointer` resolution

There are no integration tests for the analyzer or the UI at this time.

## Debugging

### Sandbox debugging

1. In IntelliJ, open the Gradle tool window.
2. Right-click `runIde` → **Debug**.
3. Set breakpoints in any of the four source files.
4. In the sandbox IDE, trigger a Housekeeping action to hit them.

### Logging

`HousekeepingAnalyzer` uses IntelliJ's `Logger`:
```kotlin
private val LOG = Logger.getInstance(HousekeepingAnalyzer::class.java)
```

Log output appears in **Help → Show Log in Explorer/Finder** (sandbox log) under the `idea.log` file. The analyzer logs a summary line at `INFO` level after each analysis run:
```
Housekeeping analysis complete: found 7 unused methods
```

Add `LOG.debug(...)` calls freely during development — debug logging is off by default and can be enabled in **Help → Diagnostic Tools → Debug Log Settings**.

## Design Decisions

### Why `SmartPsiElementPointer` instead of raw `PsiElement`?

IntelliJ may invalidate `PsiElement` instances whenever files are saved, reloaded, or modified. Storing raw pointers leads to `PsiInvalidElementAccessException` crashes. `SmartPsiElementPointer` is the correct long-lived reference; it tracks document changes and returns `null` when the element no longer exists.

### Why per-file `ReadAction` blocks in `analyzeDirectory`?

Holding a single `ReadAction` across an entire directory tree can starve the EDT for large modules. Breaking the read lock per file allows IntelliJ to schedule write actions (e.g. auto-save) between files, keeping the IDE responsive.

### Why UAST instead of direct PSI?

UAST (`UFile`, `UClass`, `UMethod`) is a language-agnostic abstraction over both Kotlin and Java PSI. Using UAST means a single code path handles both languages. If Swift or another JVM language gains UAST support in the future, the analyzer would handle it without changes.

### Why not `DumbAware`?

Analysis depends on `ReferencesSearch`, `OverridingMethodsSearch`, and `PsiSearchHelper`, all of which require a complete index. Declaring the action as `DumbAware` and running during indexing would produce incorrect results (everything would appear unused). The explicit `DumbService.isDumb()` guard gives the user a clear explanation.

### Why is the tool window hidden by default (`shouldBeAvailable = false`)?

The tool window is only relevant once an analysis has been run. Showing it by default would add a permanently visible (empty) panel to every project. Setting `isAvailable = true` in `showLoadingState()` is the standard IntelliJ pattern for on-demand tool windows.

### Why two-pass resource search?

`PsiSearchHelper.processElementsWithWord()` matches any element containing the resource name as a word. A resource named `icon` would produce thousands of spurious hits. The pattern-validating processor that checks for `R.type.name` (code) and `@type/name` / `?type/name` (XML) filters these down to genuine resource references.

## Extension Points

### Adding a new analysis mode

1. Add a new value to `AnalysisMode` in `HousekeepingAnalyzer.kt`.
2. Create a concrete `BaseHousekeepingAction` subclass with the new mode and appropriate `allowedExtensions`.
3. Add the corresponding `analyze*()` method to `HousekeepingAnalyzer`.
4. Register the new action in `plugin.xml` under `Housekeeping.Group`.
5. Add an icon case to `IconCheckBoxList.getItemIcon()` if a new `ItemType` is needed.

### Adding new keep annotations

Add the annotation simple name to `KEEP_ANNOTATIONS` in `HousekeepingAnalyzer`:

```kotlin
private val KEEP_ANNOTATIONS = setOf(
    "Keep", "Inject", "Provides", "OnClick", "OnTouch",
    "GET", "POST", "BindingAdapter",
    "YourNewAnnotation"   // add here
)
```

The matcher compares against the `substringAfterLast(".")` of the qualified name, so adding `"Singleton"` will match both `javax.inject.Singleton` and `dagger.Singleton`.

### Adding new Android entry point base classes

Add fully-qualified class names to `ANDROID_ENTRY_POINTS`:

```kotlin
private val ANDROID_ENTRY_POINTS = setOf(
    "android.app.Activity",
    // ...
    "com.yourlib.BaseFeatureComponent"   // add here
)
```

`InheritanceUtil.isInheritor()` checks the full hierarchy, so you only need to add the root base class — all subclasses will be covered automatically.

### Tracking additional resource types

Add the XML tag name to `TRACKED_RESOURCE_TYPES`:

```kotlin
private val TRACKED_RESOURCE_TYPES = setOf(
    "string", "color", "dimen", "style", "integer", "bool", "id",
    "array"    // add here
)
```

Add the folder name prefix to `RESOURCE_FOLDER_TYPES` to track file-based resources in new folders:

```kotlin
private val RESOURCE_FOLDER_TYPES = setOf(
    "layout", "drawable", "anim", "animator", "menu", "raw", "xml", "mipmap",
    "font"    // add here
)
```

## Common Pitfalls

### "Analysis finds nothing" on a large directory

If the project is still indexing when the action is invoked, the guard exits early with a notification. Wait for indexing to complete (progress bar at the bottom of the IDE disappears) before re-running.

### Test task fails with "Cannot find IDE distribution"

The test `doFirst` block walks the Gradle cache to find `ideaIC-2024.2.1`. If it cannot find the directory, `idea.home.path` is not set and the IntelliJ service container fails to initialize. Run `./gradlew build` first (without `test`) to trigger the IDE download, then re-run `./gradlew test`.

### `PsiInvalidElementAccessException` after editing files

This means a raw `PsiElement` was stored somewhere instead of a `SmartPsiElementPointer`. All `UnusedItem` instances use `SmartPsiElementPointer`. If you add new analysis results, always use:
```kotlin
SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiElement)
```

### False positives in multi-module projects

`GlobalSearchScope.projectScope(project)` covers all modules open in the current project window. If the project only has some modules open, references in excluded modules will not be found, making elements appear unused. Ensure all relevant modules are included in the project before running analysis.

### Resource name collisions

If two different resource types share the same name (e.g. `string/primary` and `color/primary`), the word search will find hits for both when checking either one. The pattern validator (`R.$type.$name` / `@$type/$name`) correctly disambiguates them because the type is part of the pattern.

## Plugin Compatibility

| Setting | Value |
|---|---|
| `sinceBuild` | 233 (IntelliJ 2023.3) |
| `untilBuild` | 300 |
| Kotlin K1 / K2 | Both supported (`supportsK2="true"`) |

To raise the minimum version, update `pluginSinceBuild` in `gradle.properties` and the `sinceBuild` value in the `patchPluginXml` block of `build.gradle.kts`.
