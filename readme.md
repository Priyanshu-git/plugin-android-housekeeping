# Housekeeping 🧹

An on-demand Android Studio plugin to analyze and safely remove unused code and resources.

Housekeeping is designed for Android developers who want to manually clean up technical debt with
confidence. Unlike automatic linters or build-time shrinkers (like R8), Housekeeping runs only when
triggered and keeps the human in the loop, allowing for scoped analysis and selective deletion with
full Undo support.

---

## 🚀 Key Features

### 1. Scoped Analysis

Analyze specific parts of your project without waiting for a full project scan.

- **Methods**: Specific methods within a file
- **Classes**: Specific classes or whole files
- **Packages**: Entire directories
- **Resources**: Specific XML files or resource folders

### 2. Intelligent Detection

- Uses **UAST (Unified Abstract Syntax Tree)** to support both Kotlin and Java seamlessly.
- **Methods**: Checks for references, overrides, and `@Keep` annotations.
- **Classes**: Detects Android entry points (Activities, Fragments, Views) to prevent false
  positives.
- **Resources**: Scans for usages in code (`R.id.*`) and XML layouts.

### 3. Safe Deletion

- Integrated with IntelliJ's **Safe Delete** handler.
- Checks for usages in comments and strings before deleting.
- Full Undo support (`Ctrl+Z` / `Cmd+Z`) if you make a mistake.

### 4. Interactive Tool Window

- View results in a dedicated tool window.
- See the reasoning behind why an item was flagged as unused.
- Double-click to navigate to the source code.
- Selectively delete items using checkboxes.

---

## 🛠 Installation

### Option 1: Build from Source

Clone this repository:

```bash
git clone https://github.com/yourusername/housekeeping-plugin.git
```

Open the project in IntelliJ IDEA or Android Studio.

Run the Gradle task to build the plugin:

```
./gradlew buildPlugin
```

The plugin file will be generated at:

```
build/distributions/housekeeping-plugin-1.0.0.zip
```

### Option 2: Install in Android Studio

1. Open **Android Studio**.
2. Navigate to **Settings / Preferences → Plugins**.
3. Click the **Gear icon (⚙️)** and select **Install Plugin from Disk…**
4. Choose the generated plugin `.zip` file.
5. Restart Android Studio to activate the plugin.

---

## 📖 How to Use

Housekeeping adds a dedicated context menu to both the **Project View** and **Code Editor**.

### 1. Analyze Methods or Classes

1. Open a Kotlin or Java file.
2. Right-click on a specific **method**, **class**, or anywhere in the editor background.
3. Select **Housekeeping → Find Unused Methods** or **Find Unused Classes**.
4. Results will appear in the **Housekeeping Tool Window** on the right.

### 2. Analyze Resources

1. In the **Project View**, right-click on:
    - A resource folder (e.g., `res/layout`, `res/values`), or
    - A specific XML resource file.
2. Select **Housekeeping → Find Unused Resources**.
3. Review detected items in the tool window.

### 3. Review and Delete

Within the **Housekeeping Tool Window**:

- Select an item to view the **reasoning** for why it was marked unused  
  (e.g., _“No references found. Visibility: private”_).
- Double-click an item to navigate to its source.
- Use checkboxes to select items for removal.
- Click the **Delete Selected** (trash) icon.
- Confirm deletion via IntelliJ’s **Safe Delete** dialog.

---

## 🧠 Heuristics & Assumptions

To prioritize safety and avoid false positives, Housekeeping follows these rules:

- **Android Entry Points**  
  Classes extending `Activity`, `Fragment`, `Service`, `BroadcastReceiver`, `View`, or `ViewModel`
  are treated as **used** by default.

- **Annotations**  
  Methods or classes annotated with  
  `@Keep`, `@Inject`, `@Provides`, `@OnClick` (ButterKnife), or `@BindingAdapter` are ignored.

- **Overrides**  
  Methods that override superclass methods or implement interface methods are considered **used**.

- **Resources**  
  Resource usage is detected via text search for `R.type.name` or `@type/name`.  
  Dynamic lookups (e.g., `getIdentifier`) are not supported and may result in false positives.

---

## ⚠️ Limitations

- **Reflection**: Detected on a best-effort basis using text search.
- **Dynamic Resources**: Dynamically constructed resource names may be incorrectly flagged.
- **Build Configuration**: `build.gradle`, ProGuard, and R8 rules are not analyzed.

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feature/AmazingFeature
    ```
3. Commit your changes
4. Open a Pull Request 
