# Data Preprocessor — IntelliJ Plugin

An in-IDE data-cleaning and transformation toolkit for data scientists, built for the JetBrains Marketplace.

## Features

- **Browse & load** any CSV file without leaving the IDE
- **Column Profiler** — type, null count, unique count, mean, median, std, min, max, mode
- **Missing value handling** — drop rows, fill with mean / median / mode / custom value
- **Remove duplicates**
- **Outlier removal** using the IQR fence method
- **Normalization** — Min-Max [0, 1] or Z-Score (mean=0, std=1)
- **Type casting** — int, float, boolean, string
- **Python code generation** — one click produces ready-to-run pandas code you can insert directly into your `.py` file

## Quick Start

### Prerequisites

- JDK 17+
- IntelliJ IDEA (any edition) 2023.3+

### Run in development

```bash
./gradlew runIde
```

### Build distributable ZIP

```bash
./gradlew buildPlugin
# Output: build/distributions/data-preprocessor-plugin-1.0.0.zip
```

### Publish to JetBrains Marketplace

1. Create an account at https://plugins.jetbrains.com
2. Generate a token in your Marketplace account settings
3. Export the token as `PUBLISH_TOKEN` env variable
4. Run:

```bash
./gradlew publishPlugin
```

## Project Structure

```
src/main/java/com/datapreprocessor/
├── model/
│   ├── DataSet.java          # In-memory tabular data model
│   └── ColumnProfile.java    # Per-column statistics
├── engine/
│   ├── DataLoader.java       # CSV → DataSet (Apache Commons CSV)
│   ├── DataCleaner.java      # All cleaning & transformation logic
│   └── CodeGenerator.java    # Generates pandas Python code
├── actions/
│   ├── OpenDataFileAction.java              # Right-click "Open in Data Preprocessor"
│   └── GeneratePreprocessingCodeAction.java # Insert code at editor caret
└── toolwindow/
    ├── DataPreprocessorToolWindowFactory.java
    └── DataPreprocessorToolWindow.java      # Main Swing UI (4 tabs)
```

## Extending the Plugin

To add a new operation:
1. Add a variant to `CodeGenerator.Operation`
2. Implement the logic in `DataCleaner`
3. Add the pandas translation in `CodeGenerator.toCode()`
4. Add a label in the `opSelector` in `DataPreprocessorToolWindow`
5. Handle the new index in `addStep()` and `applyAndGenerate()`

## License

MIT
