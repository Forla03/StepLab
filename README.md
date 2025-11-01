# StepLab

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Room](https://img.shields.io/badge/Database-Room-orange.svg)](https://developer.android.com/training/data-storage/room)

## Overview

StepLab is an Android application designed for experimenting with and evaluating pedestrian step-detection algorithms. It enables users to record sensor data, perform live step detection with configurable algorithms and filtering options, and compare the performance of different configurations on previously recorded tests. The application is primarily intended for research and educational purposes, facilitating rapid prototyping of step-detection pipelines and providing clear visualization of their outputs.

## Features

### Main Screen
The main screen serves as the application hub, providing navigation to all core functionalities. It dynamically enables or disables features based on available data (e.g., comparison and export require saved tests).

### Record New Tests
Record raw sensor data from accelerometer, magnetometer, gravity, and rotation sensors. During recording, the app displays a real-time chart of acceleration magnitude. When saving, users provide the actual step count and optional notes. The app stores test data as JSON files in internal storage and saves metadata in the Room database.

### Create a Configuration
The configuration screen is reused for both Live Testing and Configuration Comparison modes. Users can select:
- **Sampling frequency** (for live testing only; estimated from file in batch mode)
- **Filter type**: Bagilevi, Low-pass, None, Rotation Matrix, or Butterworth
- **Recognition algorithm**: Peak detection, Peak + Crossing, or Temporal filtering
- **Additional algorithms**: False-step detection (optional)
- **Autocorrelation algorithm**: Special mode requiring the entire walk, not compatible with real-time processing

A label indicates whether the configuration runs in "Real Time" or "Not Real Time" mode based on the selected algorithms.

### Live Testing
Runs a configurable pedometer in real-time with a two-phase workflow:
1. **Configuration**: Select sampling frequency, filter, recognition algorithm, and optional false-step detection
2. **Execution**: The pedometer displays a real-time acceleration magnitude chart and updates the step count when steps are detected

The architecture implements the State pattern, with `LiveTesting` managing transitions between configuration (`EnterSettingsFragment`) and execution (`PedometerRunningFragment`) states.

### Configuration Comparison
Compare up to 6 different configurations on a recorded test:
1. Select configurations (similar to Live Testing but sampling frequency is estimated from file)
2. Choose a test from the database
3. View results plotted on a chart with acceleration magnitude baseline
4. Each configuration is assigned a unique color
5. Save comparisons for later review

The comparison workflow:
- **SelectConfigurationsToCompare**: Build configuration list
- **SelectTest**: Choose test to analyze
- **ConfigurationsComparison**: Process and visualize results

### Saved Comparisons
View previously saved configuration comparisons in a card-based list. Each card shows:
- Comparison name
- Preview with test name and configurations used
- Ability to reopen the comparison chart (read-only, without save/export buttons)

### Import and Export Tests
- **Import**: Select test files through Android's file picker. Supports JSON and CSV formats (CSV is automatically converted to JSON). Compatible with tests recorded by other apps like [MotionTracker](https://github.com/MotionTracker-Repository).
- **Export**: Select one or more tests and choose export format (JSON or CSV). Files are shared via Android's share sheet using `FileProvider` for secure access.

## Architecture

### General Architecture
The application is organized into four functional packages:
- **UI**: User interface components
- **Algorithms**: Signal processing and step detection logic
- **Data**: Database persistence layer
- **Utils**: Data format conversion utilities

### Technologies Used
- **Language**: Kotlin
- **Build System**: Gradle Kotlin DSL with centralized version catalog
- **UI**: XML layouts with AppCompat and Material Components
- **Charts**: MPAndroidChart for real-time data visualization
- **Database**: Room ORM with migration support
- **Signal Processing**: JTransforms (FFT), iirj (digital filters), custom algorithms
- **Concurrency**: Kotlin Coroutines with structured concurrency

### Package Details

#### UI Package
Contains all user interface components organized by function:

**Main**
- **StepLabApplication**: Application class extending `Application`. Initializes the Room database singleton in `onCreate()` using the thread-safe Singleton pattern. Ensures the database is initialized once before any Activity starts. Registers migrations (MIGRATION_1_2 and MIGRATION_2_3) for schema evolution without data loss. Declared in the manifest with `android:name` attribute. Follows the Application Singleton pattern, a best practice that separates global initialization from UI logic.
- **MainActivity**: Application hub. Verifies saved data availability via IO coroutine to enable/disable dependent features. Handles navigation to all major features. Implements complex multi-file import using `ActivityResultLauncher` with `GetMultipleContents()` contract and progress dialog.

**Configuration**
- **EnterSettingsFragment**: Centralized configuration form implementing MVC pattern. Supports two scenarios via boolean flags (`isLiveTesting`, `showSamplingRate`). Automatically determines operational mode (Real-Time vs Not Real-Time). Treats autocorrelation as exclusive special mode with separate pipeline.
- **SelectConfigurationsToCompare**: Configuration builder activity hosting `EnterSettingsFragment` in a `FrameLayout`. Collects up to 6 configurations before starting comparison.
- **SelectTest**: Intermediate activity for test selection. Loads tests from database via IO coroutine and displays in `RecyclerView` with `AdapterForTestCard`.
- **ConfigurationsComparison**: Final workflow activity. Loads test data, initializes MPAndroidChart, draws acceleration magnitude baseline, processes configurations using `StepDetectionProcessor` facade, and visualizes results. Supports saving comparisons with unique names.

**Test**
- **LiveTesting**: Implements State pattern managing transitions between configuration and execution fragments. Reuses `EnterSettingsFragment` for configuration selection.
- **PedometerRunningFragment**: Execution state implementing Observer pattern via `SensorEventListener`. Registers/unregisters sensors in `onResume()`/`onPause()` for battery optimization. Delegates processing to `StepDetectionProcessor`.
- **NewTest**: Records sensor data at `SENSOR_DELAY_GAME` frequency. Stores events in `LinkedHashMap<Long, JSONObject>` indexed by timestamp. Requests optional metadata before saving as `EntityTest`.
- **SendTest**: Lists saved tests in `RecyclerView`. Supports multi-selection and format choice (JSON/CSV) via dialog. Exports using `FileProvider` and `ACTION_SEND_MULTIPLE` intent.
- **SavedTests**: Displays saved comparisons in interactive list. Managed by `SavedTestsAdapter` with expandable cards showing configurations. Supports deletion and read-only viewing.

#### Algorithms Package
Core computational package encapsulating signal processing logic:

**Configuration and Sensor Data**
- **Configuration**: Data class storing algorithm parameters and runtime state. Serves as the pipeline's backbone, modified by algorithms to maintain adaptive, coherent processing.
- **SensorData**: Container for sensor data maintaining separation between raw and filtered values. Tracks 3D vectors, filtered versions, world-reference components, and magnitudes. Includes validity flags to prevent processing uninitialized data.

**Step Detection Processor**
Facade coordinating specialized components. Receives `Configuration` dependency at construction. Defines two distinct processing methods:
- **ProcessRealTimeSensorData()**: Invoked by `PedometerRunningFragment` for each sensor event. Uses pre-allocated array pool to reduce GC pressure. Applies filter pipeline, optional intersection correction, false-positive validation, and returns `ProcessingResult`.
- **ProcessBatchSensorData()**: Processes recorded test JSON entries sequentially. Flexible parsing supports various file formats. Dynamically estimates sampling frequency from timestamps. Same pipeline as real-time after parsing.
- **ProcessAutocorrelationAlgorithm()**: Separate pipeline requiring complete file, not frame-by-frame processing.

**Algorithm Classes**
- **StepDetection**: Core step detection logic implementing various strategies from simple peak detection to complex autocorrelation analysis.
- **KeyValueRecognition**: Analyzes key signal values, detecting peaks and valleys characteristic of human walking.
- **Filters**: Signal conditioning operations eliminating noise before detection. Maintains multiple filter types and states.
- **Calculations**: Mathematical functions and numerical support used across all pipeline modules.

#### Data Package
Manages local persistence: entities, Room, DAO, and database configuration.

**Entities**
- **EntityTest**: Stores acquired tests with JSON payload (`testValues`) containing heterogeneous samples. Preserves faithful trace avoiding frequent schema migrations. Dedicated columns for UI-relevant metadata (step count, notes, filename).
- **EntitySavedConfigurationComparison**: "Head+body" model with atomic fields (name, test reference, timestamp) and `configurationsJson` containing serialized configuration list. Enables opening snapshots in future app versions.

**Database Configuration**
- **MyDatabase**: Room database at version 3 with two entities
- **DatabaseDao**: Minimal DAO with suspend methods for coroutine integration
- **ConfigurationSerializer**: Stateless serializer/deserializer for Configuration to/from JSON. Saves high-precision numbers as strings to preserve precision.

**Schema Evolution**
Two explicit migrations ensure additive, non-destructive evolution:
- **MIGRATION_1_2**: Introduces `saved_configuration_comparisons` table with foreign key to `EntityTest` and index on `testId` for optimized lookups. Handles transition from version 1 (EntityTest only) to version 2 (with comparison support).
- **MIGRATION_2_3**: Strengthens referential integrity by adding CASCADE foreign key constraint. Since SQLite doesn't support adding foreign keys to existing tables, this migration recreates the table: creates new version with constraint, copies all data, drops old table, renames new table, and recreates index. Ensures test deletion automatically removes associated comparisons, preventing orphaned data.

Both migrations are registered in `StepLabApplication` and applied automatically by Room, guaranteeing schema evolution that protects user data across app updates. The database aggregates two domain entities with CASCADE foreign key ensuring referential integrity: deleting a test automatically removes all associated comparisons, maintaining database consistency. This is intentional: a comparison without its reference test loses meaning, and cascade deletion reflects the domain constraint.

#### Utils Package
Support components for data format conversion:
- **CsvToJsonConverter**: Imports external CSV tests
- **JsonToCsvConverter**: Exports results in tabular format
Both handle different file schemas flexibly through automatic header analysis. Maintains acquisition and sharing pipeline independent of original data format. Compatible with [MotionTracker](https://github.com/MotionTracker-Repository) CSV files.

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android device or emulator with sensor support
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)

### Building and Running

1. Clone the repository:
   ```bash
   git clone https://github.com/Forla03/StepLab.git
   cd StepLab
   ```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Connect an Android device or start an emulator

5. Build and run the app

### Usage Workflow

#### Recording a Test
1. Tap **Register New Test** from main menu
2. Press **Start New Test** to begin recording
3. Walk naturally while the app records sensor data
4. Tap **Stop New Test** when finished
5. Enter the actual step count and optional notes
6. Press **Save New Test**

#### Live Pedometer
1. Tap **Enter Configuration**
2. Select sampling rate, filter, and algorithm
3. Press **Start Pedometer**
4. Walk and observe real-time step counting
5. Use **New Pedometer** to change configuration

#### Comparing Configurations
1. Tap **Compare Configurations**
2. Create up to 6 configurations using **Add Configuration**
3. Press **Start Comparison**
4. Select a recorded test
5. View results plotted with different colors
6. Optionally save the comparison

#### Import/Export
- **Import**: Tap **Import Test**, select JSON/CSV files
- **Export**: Tap **Export Test**, select tests and format (JSON/CSV)

## Database Architecture

StepLab uses Room with a carefully designed schema supporting evolution without data loss:

### Version History
- **Version 1**: Initial schema with EntityTest only
- **Version 2**: Added EntitySavedConfigurationComparison with foreign key and index
- **Version 3**: Strengthened referential integrity with CASCADE constraint

### Migration Strategy
- Migrations are additive and non-destructive
- All migrations registered in `StepLabApplication.onCreate()`
- Automatic application ensures seamless updates
- Table recreation pattern handles SQLite foreign key limitations

### Design Decisions
- JSON payloads for flexible, evolvable data structures
- Dedicated columns for stable UI-relevant metadata
- Foreign keys with CASCADE for domain-consistent deletions
- Suspend DAO methods for coroutine-based threading
- Stateless serializers for forward/backward compatibility

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository and create a feature branch
2. Follow existing code organization:
   - Algorithmic code → `algorithms` package
   - UI logic → `ui` package
   - Data models → `data` package
   - Conversions → `utils` package
3. Maintain architectural patterns (Singleton, Facade, State, Observer, MVC)
4. Write clear commit messages
5. Add tests for new features when applicable
6. Submit a pull request with detailed description

## Acknowledgements

StepLab builds upon prior research and open-source work:

### Pedometers Project
Based on work by Giacomo Neri ([Pedometers](https://github.com/GiacomoNeriUnibo/Pedometers)). Several core components – including low-pass and Bagilevi filters, local maxima/minima detection, zero-crossing detection, and live pedometer UI – are ports or refinements of the original Java classes (`Filtri.java`, `IndividuazionePasso.java`, `RiconoscimentoValoriChiave.java`, `LiveTesting.java`).

### Autocorrelation Research
The autocorrelation-based step counter follows the methodology from "Autocorrelation analysis of accelerometer signal to detect and count steps of smartphone users" ([Repository](https://repositorium.sdum.uminho.pt/handle/1822/70549)). Implementation includes band-pass filtering, autocorrelation-based cadence estimation, moving standard deviation for activity segmentation, and robust step counting with cadence clamping.

## License

This project is available for academic and research purposes. Please cite this repository if you use it in your work.

## Contact

For questions, issues, or collaboration opportunities:
- **Email**: francesco.forlani5@studio.unibo.it
- **GitHub**: [Forla03](https://github.com/Forla03)

---

**StepLab** - An integrated environment for collecting, processing, and analyzing sensor data for pedestrian step detection research.