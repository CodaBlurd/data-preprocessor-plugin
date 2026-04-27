# Changelog

All notable changes to **Data Preprocessor** are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0] — 2026-04-27

### Added
- **Label Encoding** — encode any categorical column to consecutive integer indices
  (insertion-order stable; e.g. `["a","b","a"]` → `["0","1","0"]`).
  Generated pandas code uses `pd.factorize()`.
- **One-Hot Encoding** — expand a categorical column into one binary column per unique
  value (`city` → `city_Austin`, `city_Chicago`, …). Dummy columns are inserted at the
  original column's position, sorted alphabetically for stable output.
  Generated pandas code uses `pd.get_dummies(df, columns=[...], dtype=int)`.
- **Train / Test Split** — split the working dataset with a configurable train ratio
  (default 0.8). A dedicated ratio field appears only when this operation is selected
  and is validated before the step is added. Java preview is a no-op (dataset unchanged);
  the generated Python script uses `sklearn.model_selection.train_test_split`.
- **Sort Column** — sort the dataset by any column, ascending or descending.
  Numeric columns sort numerically; text columns sort lexicographically.
  Null / blank values are placed last in both directions.
  Generated pandas code uses `df.sort_values(by=..., ascending=...).reset_index(drop=True)`.
- **Filter Rows** — keep only rows where a column satisfies a condition.
  Operators: `==`, `!=`, `>`, `<`, `>=`, `<=`, `contains` (case-insensitive substring).
  Numeric comparisons require parseable numbers on both sides; rows that can't be compared
  are kept rather than silently dropped. Null / blank cells are always kept
  (combine with Drop Missing Rows to remove them).
  Generated pandas code uses `df[df[col] op value]` or `str.contains()` for `contains`.
  Both steps show a row-count delta via `print()` for debugging.

### Changed
- **Tool window refactored** into five focused panel classes
  (`HeaderBarPanel`, `PreviewPanel`, `ProfilePanel`, `CleanPanel`, `CodePanel`).
  `DataPreprocessorToolWindow` is now a thin coordinator (~260 lines) that wires the
  panels via constructor-injected callbacks. No panel imports any sibling directly.

### Fixed
- `ONE_HOT_ENCODE` code generation produced invalid Python output: the Java text block
  embedded string-concatenation syntax (`\n" +`) as literal characters. Replaced with
  plain `String.format()` + `+` concatenation.
- Label Encode and One-Hot Encode operations were unreachable from the UI: the operation
  dropdown only had entries for indices 0–10; cases 11 and 12 in the switch were dead
  code. Added the missing dropdown entries.
- Train ratio validation ran unconditionally for every operation, rejecting valid column
  names as "not a number." Scoped the validation block to `opIdx == 10`.
- `describeStep()` switch expression was non-exhaustive after the new operations were
  added, causing a compile error. Added the missing `LABEL_ENCODE` and `ONE_HOT_ENCODE`
  cases.

---

## [1.1.1] — 2026-04-24

### Fixed
- `untilBuild` was being auto-filled to `233.*` by the Gradle plugin when the property
  was omitted, capping compatibility at build 233 and blocking update notifications on
  all newer IDEs. Explicitly set to empty string to remove the upper bound entirely.

---

## [1.1.0] — 2026-04-24

### Added
- **Excel support** — load `.xlsx` files directly into the tool window via Browse or
  right-click → *Open in Data Preprocessor*. All cleaning and profiling operations
  apply identically to Excel data.
- **JSON support** — load `.json` files (array-of-objects format). Headers are derived
  from the union of all keys across every object, so sparse JSON is handled correctly.
- **Format-aware code generation** — the generated pandas script now uses
  `pd.read_excel()` or `pd.read_json()` automatically when the source file is not CSV.
  Cleaned output is always written as CSV for maximum portability.
- **Format badge** — a small `CSV` / `XLSX` / `JSON` label in the header bar shows the
  active file type at a glance.
- **Sample data** — three test files (`employees.csv`, `employees.xlsx`, `employees.json`)
  added to `sample-data/` for manual testing during development.

### Fixed
- Reload was displaying binary data for Excel and JSON files — `refreshFromDisk()` was
  hardcoded to `loadCsv()` instead of the format-aware `load()` dispatcher.
- `saveAsPythonFile()` and `exportCleanedCsv()` were performing file I/O on the EDT,
  triggering `SlowOperations` SEVERE errors. Both now run file writes in a `SwingWorker`.
- `FileEditorManager.openFile()` called outside a write-safe context after background save;
  wrapped in `ApplicationManager.invokeLater()`.
- Stale error message in Generate Code action still referenced "CSV file" — updated to
  "data file (CSV, Excel, or JSON)".

### Changed
- Preview table columns auto-size on load (50-row sample, 250 px max per column).
- Code area now uses IDE-theme-aware colours (`Editor.background` / `Editor.foreground`)
  so it renders correctly in both light and dark themes.
- Path label shows filename only; full path available on hover as a tooltip.
- Status bar gets a subtle top border to visually separate it from the content area.

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
