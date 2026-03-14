# Architecture and Components

## Repository Structure

```
housekeeping-plugin/
├── build.gradle.kts                        # Build config, IntelliJ plugin setup
├── gradle.properties                       # JVM args, plugin version, build range
├── settings.gradle.kts                     # Project name
├── src/
│   ├── main/
│   │   ├── kotlin/com/example/housekeeping/
│   │   │   ├── HousekeepingAction.kt       # Action classes + base action
│   │   │   ├── HousekeepingAnalyzer.kt     # Core analysis engine
│   │   │   ├── HousekeepingModel.kt        # Data model (UnusedItem, ItemType)
│   │   │   └── HousekeepingToolWindow.kt   # Tool window factory + panel + list
│   │   └── resources/META-INF/plugin.xml   # Plugin descriptor
│   └── test/
│       └── kotlin/com/example/housekeeping/
│           └── ModelTest.kt                # Unit tests (BasePlatformTestCase)
└── docs/                                   # This documentation
```

All production source lives in four Kotlin files — the plugin is intentionally minimal.

## Plugin Descriptor (`plugin.xml`)

| Field | Value |
|---|---|
| Plugin ID | `com.nexxlabs.housekeeping` |
| Name | Housekeeping |
| Version | 1.0.0 |
| Compatibility | builds 233–300 (IntelliJ 2023.3 – 2030.x) |
| Dependencies | `platform`, `java`, `xml`, Kotlin plugin |
| Kotlin mode | Supports both K1 and K2 compiler |

### Registered extension points

**Tool Window**
```xml
<toolWindow id="Housekeeping"
    secondary="true"
    anchor="right"
    factoryClass="com.example.housekeeping.HousekeepingToolWindowFactory"/>
```
The tool window is anchored to the right stripe, registered as secondary (hidden by default until `isAvailable` is set to `true`).

**Actions** — registered in both Project View and Editor popup menus:
```xml
<group id="Housekeeping.Group" text="Housekeeping" popup="true">
    <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
    <action id="Housekeeping.FindMethods"   class="FindUnusedMethodsAction"/>
    <action id="Housekeeping.FindClasses"   class="FindUnusedClassesAction"/>
    <action id="Housekeeping.FindResources" class="FindUnusedResourcesAction"/>
</group>
```

## Classes

### `HousekeepingModel.kt`

**`ItemType`** — enum with four values: `METHOD`, `CLASS`, `RESOURCE`, `OTHER`.

**`UnusedItem`** — data class representing a single analysis result.

| Field | Type | Purpose |
|---|---|---|
| `elementPointer` | `SmartPsiElementPointer<PsiElement>` | Stable reference to the PSI element; survives document edits |
| `name` | `String` | Display name (e.g. `myMethod()`, `color/primary`) |
| `path` | `String` | Absolute virtual file path at analysis time |
| `type` | `ItemType` | Used for icon selection and display formatting |
| `reason` | `String` | Human-readable explanation shown in the details pane |
| `element` (computed) | `PsiElement?` | Dereferences the pointer; may return `null` if invalidated |

Using `SmartPsiElementPointer` instead of a raw `PsiElement` is critical: raw pointers become invalid whenever IntelliJ reloads the PSI tree (e.g. after a save or external file change), causing crashes or silent misdeletions.

### `HousekeepingAction.kt`

**`AnalysisMode`** (declared in `HousekeepingAnalyzer.kt`) — enum driving which analysis path runs:

| Value | `displayName` | Scope |
|---|---|---|
| `METHODS` | `"Methods"` | `UMethod` nodes |
| `CLASSES` | `"Classes"` | `UClass` nodes |
| `RESOURCES` | `"Resources"` | `XmlFile` nodes |

**`BaseHousekeepingAction`** — abstract base. Concrete subclasses pass a fixed `AnalysisMode` and a set of allowed file extensions:

```kotlin
abstract class BaseHousekeepingAction(
    private val mode: AnalysisMode,
    private val allowedExtensions: Set<String>
) : AnAction()
```

| Concrete class | Mode | Extensions |
|---|---|---|
| `FindUnusedMethodsAction` | `METHODS` | `java`, `kt` |
| `FindUnusedClassesAction` | `CLASSES` | `java`, `kt` |
| `FindUnusedResourcesAction` | `RESOURCES` | `xml` |

`update()` (runs on `ActionUpdateThread.BGT`) enables the action only when the selected virtual file is a directory or has a matching extension. This prevents the "Find Unused Resources" action from appearing on `.kt` files, for example.

### `HousekeepingAnalyzer.kt`

The analysis engine. Stateless except for the injected `Project`. Key companion object constants:

| Constant | Purpose |
|---|---|
| `KEEP_ANNOTATIONS` | Annotation simple names that mark an element as intentionally kept (`Keep`, `Inject`, `Provides`, `OnClick`, `OnTouch`, `GET`, `POST`, `BindingAdapter`) |
| `ANDROID_ENTRY_POINTS` | Fully-qualified base class names for Android framework classes (Activity, Fragment, Service, etc.) |
| `RESOURCE_FOLDER_TYPES` | Folder name prefixes treated as file-resource containers (`layout`, `drawable`, `anim`, etc.) |
| `TRACKED_RESOURCE_TYPES` | Value-resource tag names analyzed inside `values/*.xml` (`string`, `color`, `dimen`, `style`, `integer`, `bool`, `id`) |
| `SYNTHETIC_METHOD_NAMES` | Names of compiler-generated methods excluded from method analysis (`values`, `valueOf`, `hashCode`, `copy`, `<init>`, etc.) |

### `HousekeepingToolWindow.kt`

**`HousekeepingToolWindowFactory`** — implements `ToolWindowFactory`. Sets `shouldBeAvailable = false` so the window is invisible until an analysis action fires. On first content creation it instantiates `HousekeepingToolWindowPanel`.

**`IconCheckBoxList`** — private subclass of `CheckBoxList<UnusedItem>`. Overrides `adjustRendering()` to inject an `AllIcons.Nodes.*` icon between the checkbox and the item text. Components are pre-allocated (not recreated per row) to avoid GC pressure during list scrolling.

| `ItemType` | Icon |
|---|---|
| `CLASS` | `AllIcons.Nodes.Class` |
| `METHOD` | `AllIcons.Nodes.Method` |
| `RESOURCE` | `AllIcons.Nodes.ResourceBundle` |
| other | `AllIcons.Nodes.Tag` |

**`HousekeepingToolWindowPanel`** — extends `SimpleToolWindowPanel`. The main UI panel containing:

- **Toolbar** — three `AnAction` buttons: Delete Selected, Select All, Deselect All.
- **`IconCheckBoxList`** — scrollable list of analysis results.
- **`JBTextArea`** (details pane) — shows item details on single-click, confirmation text after deletion.
- **`JBSplitter`** — divides the list (60%) from the details pane (40%).

Public methods called by actions:

| Method | Called when |
|---|---|
| `showLoading(mode)` | Immediately after action fires, before background task starts |
| `updateResults(items, mode)` | On EDT after analysis completes |

## IntelliJ Platform APIs Used

| API | Usage |
|---|---|
| `UAST` (`UFile`, `UClass`, `UMethod`) | Language-agnostic AST for Kotlin + Java |
| `ReferencesSearch` | Find all usages of a PSI element in project scope |
| `OverridingMethodsSearch` | Check whether a method has subclass overrides |
| `PsiSearchHelper.processElementsWithWord()` | Word-based search for resource name strings |
| `InheritanceUtil.isInheritor()` | Full hierarchy check for Android entry point detection |
| `SmartPointerManager` | Create `SmartPsiElementPointer` from PSI elements |
| `ProgressManager` / `Task.Backgroundable` | Run analysis off the EDT with cancellation support |
| `ReadAction` / `ReadAction.compute()` | Thread-safe PSI reads |
| `DeleteHandler.deletePsiElement()` | Safe Delete with undo and conflict resolution |
| `DumbService` | Guard against running during indexing |
| `ToolWindowManager` | Locate and show the Housekeeping tool window |
