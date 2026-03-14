# Housekeeping Plugin — Documentation

IntelliJ/Android Studio plugin for finding and safely removing unused code and resources.

## Documents

| File | Description |
|---|---|
| [01-project-overview.md](01-project-overview.md) | Purpose, key features, capabilities, and limitations |
| [02-architecture-and-components.md](02-architecture-and-components.md) | Repository structure, classes, plugin registration, UI components |
| [03-workflow-and-data-flow.md](03-workflow-and-data-flow.md) | Internal analysis pipeline, detection logic, deletion flow |
| [04-usage-and-user-guide.md](04-usage-and-user-guide.md) | Installation, how to run analyses, UI walkthrough |
| [05-development-and-maintenance.md](05-development-and-maintenance.md) | Build, test, debug, extend, and common pitfalls |

## Quick Start

1. Build and load the plugin: `./gradlew runIde`
2. Right-click a file or directory in the Project View.
3. Choose **Housekeeping → Find Unused Methods / Classes / Resources**.
4. Review results in the **Housekeeping** tool window; check items and click **Delete**.
