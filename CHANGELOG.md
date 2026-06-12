# Changelog

All notable changes to **Data Preprocessor** are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.8.5] — 2026-06-12

### Changed
- **Android Studio — all Pro features unlocked** — Android Studio is built on
  IntelliJ Community code but is a Google product and never initialises JetBrains'
  licensing subsystem. `LicensingFacade.getInstance()` always returns `null` there
  and the IDE "Register" action does not exist, so no upgrade path was available to
  Android Studio users. Pro features are now unlocked unconditionally when the plugin
  detects it is running inside Android Studio.

---

## [1.8.4] — 2026-06-12

### Fixed
- **EDT violation — Browse file chooser** — `setForcedToUseIdeaFileChooser(true)`
  was set on the `FileChooserDescriptor` in `browseAndLoad()`. IntelliJ's dialog
  (`FileChooserDialogImpl`) calls `FileChooserUtil.getFileToSelect()` on open to
  restore the last-used path, which triggers a `LocalFileSystem.findFileByPath()`
  VFS read on the EDT and a SEVERE `SlowOperations` violation. Removed the flag;
  `FileChooser.chooseFile()` is already asynchronous and needs no wrapper.
- **EDT violation — Pipeline import file chooser** — the same
  `setForcedToUseIdeaFileChooser(true)` call existed in
  `PipelineFileActions.importPipeline()` for the `.dpp` file chooser, producing an
  identical slow-operations violation on open. Removed for the same reason.
- **EDT violation — Batch process file chooser** — `setForcedToUseIdeaFileChooser(true)`
  was also set on the multi-file `FileChooserDescriptor` in `CleanPanel.batchProcessFiles()`,
  causing the same SEVERE on every batch picker open. Removed.

---

## [1.8.3] — 2026-06-12

### Fixed
- **Visualise Pro activation refresh** — after a user starts a trial or activates
  a Pro license, the locked Visualise tab now swaps to the real chart UI without
  requiring an IDE restart.
- **Active trial detection** — Pro gates now also read JetBrains product license
  data and product expiration dates, covering active Marketplace trials exposed
  outside the confirmation-stamp path.

---

## [1.8.2] — 2026-06-12

### Added
- **Native Pro activation path** — locked Pro features now invoke the IDE's
  built-in **Manage Subscriptions / Register** flow so users can start a trial
  or activate a paid license inside the JetBrains IDE. The Marketplace pricing
  page remains only as a fallback if the IDE action is unavailable.
- **Visualise Pro license trigger** — selecting the locked Visualise tab now
  triggers the same JetBrains license flow automatically instead of only showing
  a passive locked panel.

### Changed
- **Marketplace product descriptor** — release metadata keeps
  `release-date="20260610"` and `release-version="18"` for the 1.8.x paid
  product line, matching the approved 1.8.0 freemium release.

---

## [1.8.0] — 2026-06-10

### Added
- **JetBrains Marketplace Pro gates** — R generation, SQL generation, Visualise
  charts, regex cleaning rules, `.dpp` pipeline import/export, and multi-file
  batch processing now route through a central `ProFeatureGate` backed by the
  IntelliJ Platform `LicensingFacade`. Locked features remain visible and explain
  that they require Data Preprocessor Pro.
- **Marketplace product descriptor** — `plugin.xml` now declares the freemium
  product descriptor for Data Preprocessor Pro using product code
  `PDATAPREPROCESS`, release date `20260610`, release version `18`, and
  `optional=true`.
- **Multi-file batch mode** — the Clean tab can now apply the current preprocessing
  pipeline to multiple `.csv`, `.xlsx`, or `.json` files in one run. Each successful
  file is exported beside the source as `<filename>_cleaned.csv`.
- **Batch-safe pipeline execution** — added `PipelineExecutor` so single-file Apply
  and batch processing share the same transformation path instead of maintaining
  separate switch logic.
- **Batch processing internals** — added `BatchProcessor`, per-file result tracking,
  missing-column validation, partial-failure handling, and summary reporting.
- **Pre-load batch discovery** — the Clean tab now shows batch processing guidance
  before a dataset is loaded. Users can import a saved `.dpp` pipeline first and
  then batch process files without opening a sample dataset.
- **Regex replace cleaning rule** — users can clean text columns with custom regex
  find/replace rules. Replacement values support Java-style `$1`, `$2` capture groups,
  and generated Python/R/SQL scripts translate those replacements to the equivalent
  backslash-group syntax.

### Fixed
- **Pro feature discoverability** — gated features are labelled in the UI instead of
  disappearing, so users can see what is available in Pro without guessing.
- **Removed local Pro override** — the temporary
  `datapreprocessor.pro.enabled` / `DATA_PREPROCESSOR_PRO` unlock path was
  removed so Pro access is controlled by JetBrains Marketplace licensing.
- **Batch work stays off the EDT** — selected files are loaded, transformed, exported,
  and refreshed from a background worker. The Clean tab disables Apply, Generate,
  Export, Copy TSV, Batch Process, and pipeline editing controls while the worker is
  in flight.
- **Per-file batch failures no longer stop the whole run** — files with unsupported
  types, load/export errors, or missing pipeline columns are reported individually
  while valid files continue processing.
- **Importing `.dpp` before loading data no longer shows false missing-column warnings** —
  column validation is deferred when no sample dataset is loaded yet.

### Tests
- Added coverage for batch success, missing-column skips, regex replacement behavior,
  regex JSON parameter round-trip, and generated Python/R/SQL regex snippets.

---

## [1.7.0] — 2026-06-06

### Added
- **Pipeline import/export** — the Clean tab can now save the current preprocessing steps as
  a `.dpp` JSON pipeline and import saved `.dpp` files back into the pipeline list. Imported
  pipelines warn when a step references a column missing from the currently loaded dataset.
- **Pipeline persistence internals** — added `PipelineSerializer`, `PipelineValidator`,
  `PipelineDocument`, and `PipelineFileActions` so `.dpp` parsing, validation, and IntelliJ
  file-dialog workflow stay separate from `CleanPanel`.

### Fixed
- **Pipeline import/export controls could be clipped** — Import Pipeline and Export Pipeline
  now render on their own controls row below the pipeline edit buttons, so Export remains
  visible in narrow tool-window widths.
- **Pipeline file I/O stays off the EDT** — `.dpp` read/write, VFS refresh, and imported
  column validation run inside background workers. The Clean tab only updates Swing state in
  worker completion callbacks.

### Tests
- Added serializer and validator coverage for `.dpp` round-trip, invalid JSON, unknown
  operations, missing `steps`, and missing-column warnings.

---

## [1.6.0] — 2026-05-30

### Added
- **📈 Visualise tab** — a new fifth tab renders a **histogram** or **box plot** for every
  numeric column in the loaded dataset. Select a column from the left panel, toggle between
  chart types with the Histogram / Box Plot buttons, and zoom or pan the chart with the mouse
  wheel. All charts are styled to match the IDE's dark editor background.
- **`DataChartFactory`** (new engine class) — static factory producing `JFreeChart` instances
  for histograms (`HistogramDataset`) and box plots (`DefaultBoxAndWhiskerCategoryDataset`).
  Box plot whiskers are clamped to the IQR fences already computed by `DataCleaner`; the
  `ColumnProfile`'s pre-computed `q1`, `q3`, `median`, `min`, and `max` values are used
  directly so no second data scan is needed.
- **Charts update after Apply** — `onApplied()` in the coordinator re-profiles the cleaned
  dataset and passes fresh profiles to `VisualisationPanel`, so charts immediately reflect the
  effect of normalization, outlier removal, or any other pipeline step.
- **Histogram mean / median markers** — two vertical lines are now drawn over the histogram:
  an orange line for the mean and a green line for the median, making the distribution shape
  immediately readable without needing to count bars.
- **Histogram bar outlines** — a thin darker-blue border is drawn around each bar so
  individual bins remain visually distinct at narrow widths.
- **Histogram bin slider** — the Visualise tab now includes a 5–60 bin slider for histograms,
  defaulting to 20 bins and disabling automatically for box plots.
- **Reset Zoom** — chart zoom can now be reset with a dedicated button that restores the chart
  auto-bounds after mouse-wheel zooming or panning.
- **Stats strip above charts** — the Visualise tab now shows a compact strip above the chart
  with mean, median, Q1, Q3, min, max, and missing percentage for the selected column.
- **Box plot mean line** — the default mean dot is hidden and replaced with an orange horizontal
  mean line, making it distinct from the blue IQR box, median line, and whiskers.
- **Visualise selection preservation** — after Apply or Reload, the previously selected numeric
  column is re-selected if it still exists; if a transformation removes it, the tab shows a
  clear "Column no longer available after Apply" message instead of silently switching context.
- **Column Profiles loading state after Apply** — the Profile tab now shows a "Refreshing
  profiles from cleaned dataset..." status while cleaned profiles are recomputed in the
  background.
- **SQL code generation** — "Generate SQL code" now produces a PostgreSQL-style CTE template
  for the current preprocessing pipeline. It covers the same pipeline operations as the UI,
  quotes column identifiers safely, derives one-hot columns from observed values, and saves
  with a `.sql` default filename from the Generated Code tab.

### Fixed
- **Histogram showed stale data after Apply** — `VisualisationPanel` now stores the `DataSet`
  it was last given via `onDataSetLoaded()` rather than reading the coordinator's
  `currentDataSet` through a supplier. `onApplied()` never writes back to `currentDataSet`,
  so using the supplier caused the histogram to display raw original data while the box plot
  correctly showed cleaned statistics.
- **Visualise tab not updated on Reload** — `reloadCurrentFile()` now calls
  `visualisationPanel.onDataSetLoaded(fresh, columnProfiles)` alongside the existing
  `previewPanel`, `profilePanel`, and `cleanPanel` refresh calls.
- **`DataChartFactory.blankChart()` potential NPE** — `ChartFactory.createBarChart()` was
  called with a `null` dataset, which can trigger a `NullPointerException` inside JFreeChart's
  renderer. Replaced with an empty `DefaultCategoryDataset`.
- **EDT violation — `loadDataSet()` profiled columns on the Event Dispatch Thread** —
  `DataCleaner.profileColumns()` is O(n·columns) (sorting-based percentiles). It now runs
  inside `SwingWorker.doInBackground()` in both the Browse button worker
  (`DataPreprocessorToolWindow.browseAndLoad`) and the context-menu action
  (`OpenDataFileAction`). The coordinator exposes a new primary overload
  `loadDataSet(DataSet, List<ColumnProfile>)` that accepts pre-computed profiles; the old
  single-argument overload is kept for backward compatibility but noted as computing profiles
  on the calling thread.
- **EDT violation — `applySteps()` ran DataCleaner transforms on the Event Dispatch Thread** —
  operations such as `oneHotEncode`, `removeDuplicates`, and `normalizeRobustScaler` can be
  O(n·columns) on large datasets and previously froze the IDE for the duration. They now run
  inside a `SwingWorker.doInBackground()` call. Apply, Generate, Export, Copy TSV, Add Step,
  Move Up, Move Down, Remove, Clear All, Undo, and Redo are disabled while the worker is in
  flight to prevent re-entrant calls and accidental pipeline edits mid-apply, and are restored
  in `done()`.
- **Stale Apply result after reload/new file** — in-flight Apply workers are now invalidated
  whenever a dataset is loaded or reloaded, so a slow transformation from an old file cannot
  re-enable Export or overwrite cleaned state for the currently loaded dataset.

### Dependencies
- Added `org.jfree:jfreechart:1.5.4` for chart rendering.

---

## [1.5.6] — 2026-05-19

### Changed
- **Minimum supported IDE bumped from IntelliJ 2023.3 (build 233) to 2024.3
  (build 243).** The plugin is compiled against the 2024.3 SDK and references
  APIs introduced after 2023.3 (notably the 2-argument `FileSaverDescriptor`
  constructor added in 1.5.1). Tightening `pluginSinceBuild=243` matches what
  the plugin is actually built against, silences the
  `verifyPluginConfiguration` "since-build is lower than the target IntelliJ
  Platform major version" warning, and prevents `NoSuchMethodError` crashes
  on 2023.x IDEs. The upper bound remains unset, so the plugin stays
  compatible with all 2024.3+ builds (including future releases).

### Build
- Added `systemProp.org.jetbrains.intellij.buildFeature.selfUpdateCheck=false`
  to `gradle.properties` so the Gradle IntelliJ Plugin's GitHub self-update
  check no longer aborts the entire build when GitHub's API is unreachable,
  rate-limited, or proxied. The check is informational only and has no effect
  on the produced artifact.

---

## [1.5.5] — 2026-05-18

### Fixed
- **Browse file chooser still froze or failed to navigate reliably** — switched the
  Browse action to IntelliJ's built-in single-file chooser, kept directories visible
  in the file filter so users can navigate normally, and anchored the dialog to the
  tool window component. This keeps the 1.5.4 no-`invokeLater` fix while avoiding
  native chooser hangs.
- **Generated Python/R `Normalize: Robust Scaler` — silent NaN/Inf when IQR = 0** —
  The Java preview already guarded against IQR = 0 (returns column unchanged), but the
  generated Python and R code did not. Python would silently produce `NaN` values;
  R would produce `Inf`. Added an explicit `if _iqr != 0:` / `if (.iqr != 0)` guard in
  both generated scripts so they match the Java preview behaviour.
- **Generated R `Label Encode` produced 1-based integers** — `as.integer(factor(x))`
  in R is 1-based (`[1,2,3,…]`), but the Java preview and generated Python both use
  0-based encoding (`[0,1,2,…]`). Fixed by using `factor(x, levels = unique(x)) - 1L`
  in the generated R code to match the other two outputs exactly.
- **Save dialog error message said "Could not save Python file" for R scripts** —
  Updated error message in `CodePanel` to "Could not save script file" regardless of
  language.
- **R button label was plain text `"R  Generate R code"`** — updated to
  `"🔵  Generate R code"` to match the changelog description and align visually with
  the Python button.
- **R code generation used `getSourcePath.get()` while Python used `ds.getFilePath()`** —
  Inconsistency: both now use `ds.getFilePath()` directly, which is the safe reference
  already null-checked earlier in the method.

---

## [1.5.4] — 2026-05-18

### Fixed
- **IDE-wide freeze introduced in 1.5.3** — the `ApplicationManager.invokeLater()`
  wrapper added around `FileChooser.chooseFiles` in 1.5.3 created a modal-dialog /
  EDT deadlock: the modal file picker blocks the EDT pump while waiting to dispatch,
  but the `invokeLater` runnable is itself queued behind the EDT pump — so neither
  side ever makes progress, and the entire IDE freezes. Reverted to the direct call.
  `FileChooser.chooseFiles` is already asynchronous; its callback fires on the EDT
  after the user picks a file and no wrapper is needed or safe.

---

## [1.5.3] — 2026-05-18 *(retracted — caused IDE freeze, superseded by 1.5.4)*

### Fixed
- ~~Browse button freezes on IntelliJ Platform 2024.2+~~ — the `invokeLater` fix
  introduced a modal-dialog deadlock that froze the IDE. See 1.5.4.

---

## [1.5.2] — 2026-05-18

### Fixed
- **File save dialogs broken** — both the Export CSV dialog (`CleanPanel`) and the Save
  Script dialog (`CodePanel`) used `findFileByNioFile()` to resolve the parent directory
  before opening the dialog. This method returns `null` for paths not yet indexed in the
  IntelliJ VFS, causing the dialog to malfunction on first use or for files outside the
  open project. Replaced with `refreshAndFindFileByNioFile()` which forces a VFS refresh
  and reliably returns the directory on all supported IDE versions.

---

## [1.5.1] — 2026-05-18

### Fixed
- **Deprecated API** — replaced the `FileSaverDescriptor(title, description, extensions...)`
  varargs constructor with the 2-argument form in both the Export CSV dialog (`CleanPanel`)
  and the Save Script dialog (`CodePanel`). The default filename already carries the correct
  extension (`.csv`, `.py`, or `.R`) so no extension filter is needed in the dialog.
  Eliminates the two deprecated API usage warnings reported by the JetBrains plugin verifier.

---

## [1.5.0] — 2026-05-18

### Added
- **R code generation** — "🔵 Generate R code" button produces a complete, ready-to-run R script
  for all 16 pipeline operations. Uses base-R functions throughout; `library()` calls for
  `readxl`, `jsonlite`, and `fastDummies` are emitted only when the script actually needs them
  (Excel source, JSON source, and one-hot encode respectively). Column references use
  back-tick quoting (`df$\`col\``) so column names with spaces or special characters are always
  valid R identifiers.
- **Save as script dialog accepts `.R` extension** — the "💾 Save as script…" button in the
  Code tab now offers both `.py` and `.R` in the file-type filter, so R scripts are no longer
  forced to save with a Python extension.

### Fixed
- **Critical: `DataPreprocessorSettings` not registered as an application service** — the
  `@Service(Service.Level.APP)` annotation alone is not sufficient in the IntelliJ Platform;
  `plugin.xml` must also declare an `<applicationService>` extension. Without it,
  `ApplicationManager.getApplication().getService(DataPreprocessorSettings.class)` returned
  `null`, causing a `NullPointerException` on every call to `DataPreprocessorSettings
  .getInstance()`. This crashed `CleanPanel` on construction and the settings page on open.
  Fixed by adding the missing `<applicationService>` entry to the `<extensions>` block.
- **R code generation produced no status feedback** — clicking "Generate R code" was silent;
  the status bar now shows "R code generated · N step(s)" after generation, matching the
  Python button's behaviour.
- **Save button labelled "Save as .py file" for R output** — renamed to "Save as script…"
  and the `FileSaverDescriptor` now accepts both `py` and `R` extensions.

### Changed
- Plugin description updated to reflect R code generation, undo/redo, and expanded
  normalization options.

---

## [1.4.0] — 2026-05-09

### Added
- **Settings page** under **Settings → Tools → Data Preprocessor** with persistent
  options for preview row limit, default normalization operation, and default
  train/test split ratio.
- **Undo / Redo for pipeline editing** — step additions, removals, clears, and
  reorder operations can now be undone and redone while building the pipeline.
- **Copy cleaned data as TSV** — after Apply, copy the cleaned dataset with headers
  as tab-separated values for direct paste into Excel or Google Sheets.
- **Save dialogs for outputs** — cleaned CSV export and generated Python script save
  now let users choose the destination path instead of always writing beside the
  source file.

### Changed
- Preview rendering now respects the configured row limit instead of a hardcoded
  value.
- Train / Test split uses the configured default ratio when the Clean tab is opened
  with an empty pipeline.
- The configured default normalization method selects the initial normalization
  operation in the Clean tab.

### Fixed
- Pipeline edits now invalidate the previously applied cleaned dataset, preventing
  stale CSV/TSV export after steps are changed.

---

## [1.3.0] — 2026-05-04

### Added
- **Robust Scaler** — scales any numeric column using `(x − median) / IQR`, making it
  resistant to outliers. Unlike Z-Score (StandardScaler), centering on the median means
  extreme values don't distort the result. IQR = 0 is handled gracefully (column
  returned unchanged). Generated pandas code uses `median()` and `quantile()`.
- **Step reordering** — ↑ Up / ↓ Down buttons in the pipeline list let you reorder
  steps without clearing and re-adding them.
- **Column type badges** — the column selector now shows a `[NUM]`, `[TXT]`, or `[BOOL]`
  badge next to every column name so users can see at a glance which operations make
  sense for each column. Badge text never leaks into step parameters.
- **Step count badge** — the Clean &amp; Transform tab label updates to
  "🧹 Clean &amp; Transform (N)" whenever steps are in the pipeline, giving a
  constant at-a-glance view of pipeline state from any tab.
- **Row × col count in status bar** — after every Apply the status bar shows
  "N rows × M cols" so data shape changes are immediately visible.

### Changed
- **Apply and Generate Python code are now separate buttons.** "▶ Apply steps" runs
  the Java transformation and jumps to the Preview tab. "🐍 Generate Python code"
  generates the pandas script and jumps to the Code tab. Both are enabled as soon as
  any step is in the pipeline; neither requires the other to have run first.
- Z-Score dropdown label renamed to "Normalize: Z-Score (StandardScaler)" so users
  searching for StandardScaler find the equivalent operation immediately.

### Fixed
- `NORMALIZE_ROBUST` was missing from the apply dispatch in `CleanPanel`, causing the
  Robust Scaler step to silently skip transformation in the Java preview.
- Corrected `normalizeRobustScaler()` Javadoc — previously described Min-Max behaviour
  ("scales to [0,1]") instead of the correct median/IQR formula.
- Export CSV button is now disabled until Apply has been run at least once, preventing
  the silent "nothing to export" error state when clicked on an uncleaned dataset.

---

## [1.2.1] — 2026-04-27

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
