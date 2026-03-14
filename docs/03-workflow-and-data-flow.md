# Workflow and Data Flow

## End-to-End Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  User right-clicks in Project View or Editor                    │
│  Selects: Housekeeping → Find Unused [Methods|Classes|Resources]│
└────────────────────────┬────────────────────────────────────────┘
                         │ AnActionEvent
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  BaseHousekeepingAction.actionPerformed()               [EDT]   │
│  1. Guard: DumbService.isDumb() → abort with notification       │
│  2. Collect scope via VIRTUAL_FILE_ARRAY (multi-select aware)   │
│     Fallback: PSI_ELEMENT from editor context                   │
│  3. Convert virtual files → PsiFile / PsiDirectory (ReadAction) │
│  4. Call showLoadingState() → tool window becomes visible       │
│  5. Enqueue Task.Backgroundable via ProgressManager             │
└────────────────────────┬────────────────────────────────────────┘
                         │ background thread
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  HousekeepingAnalyzer.analyze()                    [BG Thread]  │
│  For each scope element:                                        │
│    ProgressManager.checkCanceled()                              │
│    PsiDirectory → analyzeDirectory() (recursive)               │
│    PsiFile/UFile → analyzeUFile()                               │
│    XmlFile (Resources mode) → analyzeResourceFile()             │
│  Returns List<UnusedItem>                                       │
└────────────────────────┬────────────────────────────────────────┘
                         │ invokeLater
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  HousekeepingToolWindowPanel.updateResults()            [EDT]   │
│  Populates IconCheckBoxList                                     │
│  Updates details pane with result count                         │
└────────────────────────┬────────────────────────────────────────┘
                         │ User interacts
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  User checks items → clicks Delete Selected             [EDT]   │
│  1. Validate items (ReadAction: element.isValid + path match)   │
│  2. Confirmation dialog (lists items, warns about undo)         │
│  3. DeleteHandler.deletePsiElement() — Safe Delete              │
│  4. Refresh list (remove deleted items)                         │
└─────────────────────────────────────────────────────────────────┘
```

## Scope Collection

`BaseHousekeepingAction` first looks at `CommonDataKeys.VIRTUAL_FILE_ARRAY`. This key is populated when the user selects one or more nodes in the Project View, enabling multi-file and multi-directory analysis in a single run.

If that array is empty (e.g. the user right-clicked inside an editor tab), it falls back to `CommonDataKeys.PSI_ELEMENT`. Directories discovered via either path become `PsiDirectory` instances and trigger recursive analysis; files become `PsiFile` instances analyzed directly.

All PSI lookups happen inside a `ReadAction.compute` block to comply with IntelliJ's threading model.

## Directory Traversal

```kotlin
analyzeDirectory(directory, mode, results, indicator)
  │
  ├── ReadAction: collect directory.files + directory.subdirectories
  │
  ├── For each file:
  │     ReadAction: toUElementOfType<UFile>()
  │       → analyzeUFile(uFile, mode, results)   // Kotlin/Java
  │     or if XmlFile + RESOURCES mode:
  │       → analyzeResourceFile(file, results)
  │
  └── For each subdir: recurse
```

Per-file `ReadAction` blocks are used deliberately — holding the read lock across an entire directory tree would stall the EDT for large modules.

## Method Analysis (`analyzeMethod`)

```
UMethod
  │
  ├── name == "main"                         → skip
  ├── hasKeepAnnotations()                   → skip
  │     (@Keep, @Inject, @Provides, @OnClick, @OnTouch, @GET, @POST, @BindingAdapter)
  ├── javaPsi.findSuperMethods().isNotEmpty  → skip (overrides something)
  ├── OverridingMethodsSearch.findFirst()    → skip (something overrides this)
  │
  └── ReferencesSearch.search(psiMethod, projectScope)
        findFirst() == null → add UnusedItem(METHOD)
```

Synthetic methods are filtered in `analyzeUFile` before `analyzeMethod` is even called:
- `sourcePsi == null` (compiler-generated, no source)
- Name in `SYNTHETIC_METHOD_NAMES` (`values`, `valueOf`, `hashCode`, `equals`, `toString`, `copy`, `<init>`, `<clinit>`, `entries`)
- Name starts with `component` (Kotlin destructuring)
- Name starts with `copy$default` (Kotlin `copy` overload)

## Class Analysis (`analyzeClass`)

```
UClass
  │
  ├── sourcePsi == null || sourcePsi is PsiFile  → skip (Kotlin facade class)
  ├── isAndroidEntryPoint()                      → skip
  │     InheritanceUtil.isInheritor() for Activity, Fragment, Service,
  │     BroadcastReceiver, ContentProvider, Application, View, ViewModel
  ├── hasKeepAnnotations()                       → skip
  │
  └── ReferencesSearch.search(psiClass, projectScope)
        findFirst() == null → add UnusedItem(CLASS)
```

`InheritanceUtil.isInheritor()` walks the full supertype chain, so a class extending `AppCompatActivity` (which extends `FragmentActivity` → `Activity`) is correctly skipped even though `Activity` is not its direct parent.

## Resource Analysis

Resource analysis splits into two paths based on the XML file's parent directory name.

### Values resources (`values/*.xml`)

```
XmlFile (parent dir starts with "values")
  │
  └── For each root tag child:
        tag.name must be in TRACKED_RESOURCE_TYPES
          (string, color, dimen, style, integer, bool, id)
        tag.getAttributeValue("name") → resourceName
        │
        └── isResourceUsed(resourceName, tag.name)
              false → add UnusedItem(RESOURCE, "$type/$name")
```

### File resources (layout, drawable, anim, etc.)

```
XmlFile (parent dir matches RESOURCE_FOLDER_TYPES)
  │
  └── resourceName = virtualFile.nameWithoutExtension
      folderType  = parentDirName.substringBefore("-")
                    (strips qualifier: "drawable-hdpi" → "drawable")
      │
      └── isResourceUsed(resourceName, folderType)
            false → add UnusedItem(RESOURCE, "$folderType/$resourceName")
```

### Two-pass word search (`isResourceUsed`)

The search uses `PsiSearchHelper.processElementsWithWord()`, which finds every PSI element whose text contains the resource name as a word. A pattern-validating processor then filters each match to avoid false positives from variable names that coincidentally share the resource name.

**Pass 1 — Code references** (`UsageSearchContext.IN_CODE`):
Walks up to 4 PSI ancestors from the matched leaf. Accepts the match only if one ancestor's `.text` contains `R.<type>.<name>`.

**Pass 2 — XML references** (`UsageSearchContext.ANY`):
Walks up to 3 PSI ancestors. Accepts only if one ancestor's `.text` contains `@<type>/<name>` or `?<type>/<name>`.

If either pass finds a valid match, the resource is considered used and excluded from results.

## Deletion Workflow

```
deleteSelected()                                      [EDT required]
  │
  ├── assertIsDispatchThread()                        ← hard crash if wrong thread
  │
  ├── ReadAction: filter selected items
  │     item.element != null
  │     element.isValid == true
  │     element.containingFile.virtualFile.path == item.path
  │       (rejects elements that moved since analysis ran)
  │
  ├── Messages.showYesNoDialog()                      ← confirmation with item preview
  │     lists up to 15 items; "and N more" if larger
  │     "Ctrl+Z to undo" reminder
  │
  ├── ReadAction: collect valid PsiElement[]
  │
  ├── DeleteHandler.deletePsiElement(elements, project)
  │     IntelliJ Safe Delete: detects usages introduced after analysis,
  │     prompts for conflict resolution if found, records undo action
  │
  └── Refresh list: unchecked items remain, checked items removed
```

The path-equality check (`currentPath == item.path`) is an important safety guard. If a file is renamed or moved between when analysis ran and when the user clicks Delete, the stored path no longer matches. The item is dropped from the deletion batch and an info message is shown instead.
