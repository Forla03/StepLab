# StepLab

## Overview

StepLab is an Android application for experimenting with and evaluating pedestrian step‑detection algorithms. It allows users to record sensor data, perform live step detection with configurable algorithms and filtering options, and compare the performance of different configurations on previously recorded tests. The application is designed primarily for research and teaching purposes, enabling rapid prototyping of step‑detection pipelines and visualizing their output.

## Features

### Record New Tests
Capture raw sensor data (accelerometer, magnetometer, gravity, rotation) and store it in JSON files and a local Room database. Users specify the true number of steps and optional notes when saving a test.

### Live Step Detection
Run a configurable pedometer in real time. Users select:
- Sampling frequency
- Filters (Bagilevi, low‑pass, none, rotation‑matrix, or Butterworth)
- Recognition algorithm (peak detection only, peak + crossing, temporal filtering, or autocorrelation)
- Optional false‑step detection

The pedometer displays a real‑time chart of acceleration magnitude and updates the step count whenever a step is detected.

### Configurable Algorithm Parameters
The Configuration data class captures all algorithm parameters:
- Sampling rate
- Real‑time mode
- Recognition algorithm
- Filter type
- Cutoff frequency index
- Detection threshold
- Flags for false‑step detection and the autocorrelation algorithm

### Compare Configurations
Select multiple configurations (up to six) and evaluate them on a recorded test. StepLab processes each configuration in the background, applies the appropriate filter and recognition algorithm, and plots the resulting filtered signal alongside the raw baseline. Step counts for each configuration are listed, allowing direct comparison.

### Autocorrelation‑based Counting
Includes an implementation of the IPIN 2019 step‑detection pipeline, featuring:
- High‑pass/low‑pass band‑pass filtering
- Autocorrelation‑based fundamental frequency estimator
- Moving standard deviation to detect activity segments
- Robust step counting with cadence clamping

### Import and Export Tests
- Tests can be imported from plain text/JSON files and stored in the database
- Selected tests can be exported and shared via Android's file‑sharing facilities

### False‑step Detection
Optional techniques based on magnetometer variance and Butterworth filter characteristics help reduce false positives. Users can enable or disable these detection modes via the configuration screen.

### Data Persistence
Uses Room ORM with an EntityTest table to store test metadata, values, number of steps, and notes.

## Architecture

The application is organized into three main packages:

### Algorithms (`com.example.steplab.algorithms`)
Contains the step‑detection logic:
- **StepDetectionProcessor**: Orchestrates sensor processing, applying the selected filter and recognition algorithm and handling false‑step detection
- **Filters class**: Implements Bagilevi, low‑pass, Butterworth and band‑pass filters
- **Calculations class**: Provides helper methods for computing vector magnitudes, linear acceleration, rotation transforms, and autocorrelation analysis
- **KeyValueRecognition**: Detects local maxima/minima and zero‑crossings for step detection
- **StepDetection**: Performs different detection strategies, including the autocorrelation pipeline
- **Configuration class**: Stores algorithm parameters and runtime state

### UI (`com.example.steplab.ui`)
Activities and fragments build the user interface:
- **MainActivity**: Presents the main menu and initializes the database; routes to live testing, new test recording, configuration comparison, import, and export activities
- **LiveTesting**: Hosts a PedometerRunningFragment where real‑time processing occurs
- **NewTest**: Records a new test and writes JSON files
- **SendTest**: Displays saved tests and allows sharing them
- **SelectConfigurationsToCompare** and **ConfigurationsComparison**: Implement the configuration selection and comparison workflow

### Data (`com.example.steplab.data.local`)
Defines the Room database with EntityTest, DatabaseDao, and MyDatabase.

## Using StepLab

### Prerequisites
- Android Studio Flamingo or later
- Android device or emulator with sensors (accelerometer, magnetometer, gravity, rotation)
- MPAndroidChart library (bundled via Gradle)

### Building and Running

1. Clone this repository:
   ```bash
   git clone https://github.com/Forla03/StepLab.git
   ```

2. Open the project in Android Studio and allow it to synchronize Gradle dependencies.

3. Connect an Android device or launch an emulator.

4. Build and run the app module. The main screen will display options to enter a configuration, record a new test, compare configurations, import a test, or send a test.

### Recording a New Test

1. From the main menu, tap **Register New Test**.
2. Press **Start New Test** to begin recording. Sensor data will be displayed in a graph as it is captured.
3. When finished walking, tap **Stop New Test**. Provide the true number of steps and any notes, then tap **Save New Test**. The test data and metadata are stored in the local database and saved as a JSON file.
4. Tests appear in the compare and send test screens.

### Live Pedometer

1. Tap **Enter Configuration**. The configuration screen allows you to choose:
   - Sampling rate
   - Real‑time or non‑real‑time mode
   - Recognition algorithm
   - Filter type
   - Cutoff frequency
   - Enable false‑step detection
   - Select the autocorrelation algorithm when applicable

2. After selecting parameters, press **Start Pedometer**. The pedometer will start processing sensor data in real time, updating a chart and incrementing the step count whenever a step is detected.

3. To change configuration, press **New Pedometer** and adjust settings.

### Compare Configurations

1. Tap **Compare Configurations** on the main menu. If no tests exist, this option is disabled.
2. Select up to six configurations. For each, choose algorithm parameters in the settings fragment and press **Add Configuration**.
3. Once configurations are set, tap **Start Comparison**, choose a recorded test, and the app will process each configuration in parallel, displaying step counts and plots for each.

### Import/Export Tests

- **Import**: Use **Import Test** to pick a JSON file containing test data. The app parses the file and adds it to the database.
- **Export**: Use **Send Test** to select recorded tests and share them via Android's share sheet. The app packages the selected test files into a share intent.

## Contributing

1. Fork the repository and create a feature branch.
2. Follow the existing project structure: algorithmic code resides in `algorithms`, UI logic under `ui`, and data models under `data`.
3. When adding new detection algorithms or filters, implement them in the `algorithms` package and integrate them into `StepDetectionProcessor`.
4. Submit a pull request with a clear description of the changes.

## Acknowledgements and References

StepLab builds upon prior work from both open‑source projects and academic research:

### Pedometers Project
The Pedometers app by Giacomo Neri (https://github.com/GiacomoNeriUnibo/Pedometers) implemented an early version of the pedometer experiment used in StepLab. Several core components of StepLab – including the low‑pass and Bagilevi filters, the detection of local maxima/minima and zero‑crossings, and the layout of the live pedometer screen – are ports or refinements of Pedometers' Java classes (`Filtri.java`, `IndividuazionePasso.java`, `RiconoscimentoValoriChiave.java`, and `LiveTesting.java`). These classes dynamically adjust thresholds to detect peaks and valleys, track the sign of derivative to locate extrema, and manage the user interface for starting and restarting a pedometer session. We gratefully acknowledge this work and thank Giacomo Neri for making it available.

### Autocorrelation Analysis Research
StepLab's autocorrelation‑based step counter follows the methodology of the paper "Autocorrelation analysis of accelerometer signal to detect and count steps of smartphone users" (https://repositorium.sdum.uminho.pt/handle/1822/70549). The algorithm filters the acceleration magnitude into a selected band, computes the autocorrelation to estimate the fundamental step cadence, segments active walking using a moving standard deviation, and counts steps by placing markers at lags corresponding to the fundamental frequency. StepLab implements these steps in the `StepDetection` class and allows the band edges, filter order, and segment length to be configured.