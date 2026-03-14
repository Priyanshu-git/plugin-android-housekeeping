# Usage and User Guide

## Installation

### Step 1 — Build the plugin ZIP

From the repository root, run:

```bash
./gradlew buildPlugin
```

The distributable archive is written to:

```
build/distributions/housekeeping-plugin-1.0.0.zip
```

Do not unzip it — the IDE expects the ZIP as-is.

### Step 2 — Install in IntelliJ IDEA

1. Open **File → Settings** (Windows/Linux) or **IntelliJ IDEA → Settings** (macOS).
2. Navigate to **Plugins**.
3. Click the gear icon (⚙) at the top of the Plugins panel.
4. Choose **Install Plugin from Disk…**
5. In the file browser, select `build/distributions/housekeeping-plugin-1.0.0.zip`.
6. Click **OK**.
7. Click **Restart IDE** when prompted.

### Step 2 (alternative) — Install in Android Studio

1. Open **File → Settings** (Windows/Linux) or **Android Studio → Settings** (macOS).
2. Navigate to **Plugins**.
3. Click the gear icon (⚙) at the top of the Plugins panel.
4. Choose **Install Plugin from Disk…**
5. Select `build/distributions/housekeeping-plugin-1.0.0.zip`.
6. Click **OK**.
7. Click **Restart IDE** when prompted.

### Step 3 — Verify the installation

After the IDE restarts:

- Open **File → Settings → Plugins → Installed** and confirm **Housekeeping** appears in the list with a checkmark.
- Open any Android/Kotlin project, right-click a file or directory in the Project View, and confirm a **Housekeeping** submenu appears at the bottom of the context menu.

### Updating the plugin

Build a new ZIP with `./gradlew buildPlugin` and repeat the install steps. IntelliJ will replace the old version automatically; a restart is required.

### Uninstalling

**Settings → Plugins → Installed → Housekeeping → Disable / Uninstall → Restart IDE.**

### Running in a sandboxed IDE (development only)

```bash
./gradlew runIde
```

Launches a fresh IntelliJ Community instance with the plugin pre-loaded. No installation step is needed. Use this during development to test changes without touching your main IDE.

## Prerequisites

- IntelliJ IDEA or Android Studio, build **233 or later** (2023.3+).
- The project must be fully indexed before running analysis (the plugin is not DumbAware).

## Where to Find the Actions

The plugin adds a **Housekeeping** submenu in two places:

| Location | How to access |
|---|---|
| **Project View context menu** | Right-click any file, directory, or multi-selection |
| **Editor context menu** | Right-click inside the editor tab |

The submenu contains three actions:

| Action | What it finds |
|---|---|
| Find Unused Methods | Methods with no callers (Kotlin + Java) |
| Find Unused Classes | Classes with no references (Kotlin + Java) |
| Find Unused Resources | XML resource entries with no code or XML references |

The actions are automatically enabled or disabled based on the selected item type:

- **Find Unused Methods / Classes** — enabled for `.kt`, `.java` files and directories.
- **Find Unused Resources** — enabled for `.xml` files and directories.
- All three are enabled for directories (the analyzer filters by extension internally).

## Running an Analysis

### Step 1 — Select a scope

In the Project View, select one of:
- A single `.kt`, `.java`, or `.xml` file
- A directory (analysis recurses into all subdirectories)
- Multiple files or directories using Ctrl+Click / Shift+Click

### Step 2 — Trigger the action

Right-click the selection → **Housekeeping** → choose an action.

If the IDE is still indexing, a notification will appear:
> "Housekeeping analysis requires indexing to complete first."
Wait for indexing to finish and try again.

### Step 3 — Wait for results

The **Housekeeping** tool window opens automatically in the right stripe and shows:
> "Analyzing methods… This may take a moment for large scopes."

A progress bar appears in the status bar with a cancel button. Analysis runs file by file, so for very large directories you can see incremental progress.

### Step 4 — Review results

When analysis completes, the tool window shows a checkable list of candidates.

**Single-click** an item to see details in the panel below:
```
Name: MyUtils.formatDate()
Type: METHOD
Path: /app/src/main/java/com/example/MyUtils.kt

Reason:
No references found.
Visibility: private
```

**Double-click** an item to jump to its declaration in the editor.

### Step 5 — Select items to delete

Check individual items, or use the toolbar:

| Button | Action |
|---|---|
| **Select All** | Check all items in the list |
| **Deselect All** | Uncheck all items |
| **Delete Selected** | Delete all checked items (enabled only when at least one is checked) |

### Step 6 — Confirm and delete

Clicking **Delete Selected** opens a confirmation dialog listing the selected items (up to 15, with "and N more" for larger batches) and a reminder that the operation is undoable.

Clicking **Delete** triggers IntelliJ's Safe Delete, which:
1. Performs a second usage search to catch any references added since analysis ran.
2. If new usages are found, shows a conflict dialog so you can review before proceeding.
3. Records the deletion in IntelliJ's undo history.

Use **Ctrl+Z** (Cmd+Z on macOS) to undo a deletion.

## Expected Results and Behavior

### What gets reported

- Methods that are never called within the project scope.
- Classes that are never referenced (instantiated, extended, or used as a type) within the project.
- XML resource entries (`string`, `color`, `dimen`, `style`, `integer`, `bool`, `id`) with no matching `R.type.name` or `@type/name` references.
- XML resource files (layouts, drawables, menus, etc.) with no matching references.

### What is intentionally excluded

| Category | Reason |
|---|---|
| `main()` methods | Application entry points |
| Methods/classes annotated with `@Keep`, `@Inject`, `@Provides`, `@OnClick`, `@OnTouch`, `@BindingAdapter`, `@GET`, `@POST` | Framework-managed or serialization-used |
| Methods that override a super method | Part of an interface or superclass contract |
| Methods that are overridden by a subclass | Used polymorphically |
| Classes extending Android framework types (Activity, Fragment, Service, etc.) | Android entry points instantiated by the framework |
| Kotlin compiler-generated methods (`hashCode`, `equals`, `toString`, `copy`, `component*`, `<init>`, etc.) | Not user-authored |
| Kotlin facade classes (top-level function holders) | Not instantiable classes |

### No results found

If the details pane shows:
> "No unused methods found in the selected scope."

…then every method in the selected files passed at least one of the suppression checks, or had at least one reference. This is the correct outcome, not an error.

## Warnings and Safeguards

- **Re-run after major refactors.** Analysis results are a snapshot. If you refactor code between running analysis and clicking Delete, the plugin validates each item's path and element validity before deleting — stale items are silently skipped and a notification is shown.
- **Reflection and string-based references are not detected.** Items accessed via `Class.forName()`, `Method.invoke()`, or by dynamic string construction will appear as unused. Review carefully before deleting such candidates.
- **Resource qualifiers are stripped for matching.** A file in `drawable-hdpi/` is treated as `drawable/name`; the search looks for any usage of that resource name regardless of qualifier.
- **Deletion is reversible.** IntelliJ's Safe Delete creates an undo entry. Use Ctrl+Z if you delete something in error.
