# Changelog

All notable changes to **Data Preprocessor** are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.3] — 2026-04-21

### Fixed
- **Tool window not appearing in sidebar after installation** — icon file
  `dataPreprocessor.svg` was missing from the build, causing IntelliJ to silently
  skip tool window registration. Added light and dark theme variants of the icon.
- Reload button now re-reads the CSV from disk without clearing the pipeline —
  previously called `loadDataSet()` which wiped all pending steps on every reload.
- Path label now updates correctly when files are opened via right-click context menu
  (was only updated through the Browse button).
- Reload button is disabled until a file has been loaded, preventing broken state on
  first open.

### Changed
- Apply button is now disabled when the pipeline is empty and enables automatically
  when the first step is added, removing the silent "No steps to apply" dead end.
- Preview tab is automatically selected when any file loads so users see their data
  immediately rather than being left on whichever tab was last active.

### Added
- Review prompt: a one-time balloon notification appears after 3 CSV loads asking
  users to leave a review on JetBrains Marketplace. Never repeats after dismissal.

---

## [1.0.2] — 2026-04-20

### Fixed
- Reload button now re-reads the CSV from disk without clearing the pipeline.
- Path label updates correctly when files are opened via right-click context menu.
- Reload button disabled until a file has been loaded.

### Changed
- Apply button disabled when the pipeline is empty.
- Preview tab auto-selected when a file loads.

### Added
- One-time review prompt balloon after 3 CSV loads.

---

## [1.0.1] — 2026-04-18

### Fixed
- `OpenDataFileAction` now correctly declares `ActionUpdateThread.BGT` instead of `EDT`,
  resolving a SEVERE threading warning logged when right-clicking a CSV file in the Project view.

### Changed
- Preview tab now shows an animated walkthrough GIF when no CSV file is loaded,
  giving new users an instant overview of the workflow before they open a file.
- Updated plugin description on JetBrains Marketplace with clearer feature copy
  and an explicit privacy statement.
- Expanded IDE compatibility: `pluginUntilBuild` raised from `242.*` to `253.*`,
  covering IntelliJ Platform builds through 2025.3.

---

## [1.0.0] — 2026-04-10

### Added
- **CSV Data Loading** — browse and open any CSV file from the tool window or via
  right-click → *Open in Data Preprocessor* in the Project view.
- **Column Profiling** — per-column statistics: type, null count, null %, unique count,
  mean, median, std dev, min, max, and most common value.
- **Missing Value Handling** — drop rows with missing values, or fill with mean,
  median, mode, or a user-supplied custom value.
- **Duplicate Removal** — remove exact-match duplicate rows in one click.
- **Outlier Removal** — IQR fence method (1.5 × IQR) applied per column.
- **Normalization** — Min-Max scaling [0, 1] or Z-Score standardisation (mean=0, std=1).
- **Type Casting** — cast any column to `int`, `float`, `boolean`, or `string`.
- **Pandas Code Generation** — complete, ready-to-run Python script generated from
  the cleaning pipeline; opens automatically in the editor on save.
- **Export Cleaned CSV** — writes `<filename>_cleaned.csv` alongside the source file.
- **Right-click context menu** — *Open in Data Preprocessor* and
  *Generate Preprocessing Code* actions available on `.csv` files in the Project view.
