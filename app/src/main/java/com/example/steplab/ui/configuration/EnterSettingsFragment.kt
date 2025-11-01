package com.example.steplab.ui.configuration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.steplab.R
import com.example.steplab.algorithms.Configuration
import java.math.BigDecimal

class EnterSettingsFragment(
    private val configuration: Configuration = Configuration(),
    private val showSamplingRate: Boolean = true,
    private val isLiveTesting: Boolean = false
) : Fragment() {

    constructor() : this(Configuration(), true, false)
    constructor(configuration: Configuration) : this(configuration, true, true)

    private var first = true
    private var suppressCallbacks = false // guard to avoid listener loops

    private var scrollView: ScrollView? = null
    private var samplingTwenty: RadioButton? = null
    private var samplingForty: RadioButton? = null
    private var samplingFifty: RadioButton? = null
    private var samplingHundred: RadioButton? = null
    private var samplingMax: RadioButton? = null

    private var currentModalityIndicator: TextView? = null

    private var recognitionPeak: RadioButton? = null
    private var recognitionIntersection: RadioButton? = null

    private var filterBagilevi: RadioButton? = null
    private var filterLowPass: RadioButton? = null
    private var noFilter: RadioButton? = null
    private var filterRotation: RadioButton? = null
    private var butterworthFilter: RadioButton? = null
    private var falseStepRadio: RadioButton? = null
    private var noneAdditional: RadioButton? = null
    private var timeFiltering: RadioButton? = null
    private var autocorrelation: RadioButton? = null

    private var cutoffTwo: RadioButton? = null
    private var cutoffThree: RadioButton? = null
    private var cutoffTen: RadioButton? = null
    private var cutoffDividedFifty: RadioButton? = null

    private var cutoffFrequencyLayout: LinearLayout? = null
    private var layoutSamplingRate: LinearLayout? = null
    private var textSamplingRate: TextView? = null
    private var firstView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.enter_settings_fragment, container, false)

        scrollView = root.findViewById(R.id.scroll_view)
        samplingTwenty = root.findViewById(R.id.sampling_twenty)
        samplingForty = root.findViewById(R.id.sampling_forty)
        samplingFifty = root.findViewById(R.id.sampling_fifty)
        samplingHundred = root.findViewById(R.id.sampling_hundred)
        samplingMax = root.findViewById(R.id.sampling_max)

        currentModalityIndicator = root.findViewById(R.id.current_modality_indicator)

        recognitionPeak = root.findViewById(R.id.recognition_peak)
        recognitionIntersection = root.findViewById(R.id.recognition_intersection)

        filterBagilevi = root.findViewById(R.id.filter_bagilevi)
        filterLowPass = root.findViewById(R.id.filter_low_pass)
        noFilter = root.findViewById(R.id.no_filter)
        filterRotation = root.findViewById(R.id.filter_rotation)
        butterworthFilter = root.findViewById(R.id.butterworth_filter)
        falseStepRadio = root.findViewById(R.id.false_step_radio)
        noneAdditional = root.findViewById(R.id.none_additional)
        timeFiltering = root.findViewById(R.id.time_filtering_alg)
        autocorrelation = root.findViewById(R.id.autocorrelation)

        cutoffTwo = root.findViewById(R.id.cutoff_two)
        cutoffThree = root.findViewById(R.id.cutoff_three)
        cutoffTen = root.findViewById(R.id.cutoff_ten)
        cutoffDividedFifty = root.findViewById(R.id.cutoff_divided_fifty)

        cutoffFrequencyLayout = root.findViewById(R.id.cutoff_frequency_layout)
        layoutSamplingRate = root.findViewById(R.id.layout_sampling_rate)
        textSamplingRate = root.findViewById(R.id.text_sampling_rate)
        firstView = root.findViewById(R.id.first_view)

        if (!showSamplingRate) {
            textSamplingRate?.visibility = View.GONE
            layoutSamplingRate?.visibility = View.GONE
            firstView?.visibility = View.GONE
        } else {
            textSamplingRate?.visibility = View.VISIBLE
            layoutSamplingRate?.visibility = View.VISIBLE
            firstView?.visibility = View.VISIBLE
        }

        setupListeners()

        // Apply defaults only if autocorr is NOT active
        if (savedInstanceState == null && !configuration.autocorcAlg) {
            applyDefaultSelection()
        } else {
            syncUiFromConfiguration()
        }

        // Initial availability update (autocorrelation disabled only in live testing)
        updateOptionsAvailability()

        return root
    }

    /**
     * Determines the real-time mode automatically based on selected options.
     * If any of: time filtering, butterworth filter, false step, or autocorrelation is selected,
     * the mode switches to Not Real-Time (1), otherwise it stays in Real-Time (0).
     */
    private fun updateRealTimeMode() {
        val isNotRealTime = (timeFiltering?.isChecked == true) ||
                           (butterworthFilter?.isChecked == true) ||
                           (falseStepRadio?.isChecked == true) ||
                           (autocorrelation?.isChecked == true)
        
        configuration.realTimeMode = if (isNotRealTime) 1 else 0
        
        // Update the modality indicator
        currentModalityIndicator?.text = if (configuration.realTimeMode == 0) {
            getString(R.string.real_time)
        } else {
            getString(R.string.not_real_time)
        }
        
        // Update UI based on mode (enable/disable options)
        updateOptionsAvailability()
    }
    
    /**
     * Updates the availability of options based on current real-time mode.
     * Everything should be enabled except autocorrelation in live testing mode.
     */
    private fun updateOptionsAvailability() {
        // Autocorrelation only disabled in live testing mode
        if (isLiveTesting) {
            autocorrelation?.isEnabled = false
            autocorrelation?.alpha = 0.5f
        } else {
            autocorrelation?.isEnabled = true
            autocorrelation?.alpha = 1.0f
        }
        
        // False step is always enabled (it will trigger non-real-time mode when checked)
        falseStepRadio?.isEnabled = true
        falseStepRadio?.alpha = 1.0f
    }

    private fun setupListeners() {
        samplingTwenty?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.samplingFrequencyIndex = 0
                uncheckAllSamplingExcept(samplingTwenty)
            }
        }
        samplingForty?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.samplingFrequencyIndex = 1
                uncheckAllSamplingExcept(samplingForty)
            }
        }
        samplingFifty?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.samplingFrequencyIndex = 2
                uncheckAllSamplingExcept(samplingFifty)
            }
        }
        samplingHundred?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.samplingFrequencyIndex = 3
                uncheckAllSamplingExcept(samplingHundred)
            }
        }
        samplingMax?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.samplingFrequencyIndex = 4
                uncheckAllSamplingExcept(samplingMax)
            }
        }

        recognitionPeak?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.recognitionAlgorithm = 0
                recognitionIntersection?.isChecked = false
                timeFiltering?.isChecked = false
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
            }
        }

        recognitionIntersection?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.recognitionAlgorithm = 1
                recognitionPeak?.isChecked = false
                timeFiltering?.isChecked = false
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
            }
        }

        timeFiltering?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.recognitionAlgorithm = 2
                recognitionPeak?.isChecked = false
                recognitionIntersection?.isChecked = false
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                updateRealTimeMode()
            } else {
                updateRealTimeMode()
            }
        }

        filterBagilevi?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 0
                // Reset threshold only if it was modified by another filter
                if (configuration.detectionThreshold != BigDecimal.ZERO && 
                    configuration.detectionThreshold != BigDecimal.valueOf(5)) {
                    configuration.detectionThreshold = BigDecimal.ZERO
                }
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(filterBagilevi)
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                updateRealTimeMode()
            }
        }

        filterLowPass?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 1
                // Reset threshold only if it was modified by another filter (e.g., rotation matrix = 8)
                if (configuration.detectionThreshold != BigDecimal.ZERO && 
                    configuration.detectionThreshold != BigDecimal.valueOf(5)) {
                    configuration.detectionThreshold = BigDecimal.ZERO
                }
                showCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(filterLowPass)
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                if (!first) scrollView?.post { scrollView?.fullScroll(View.FOCUS_DOWN) } else first = false
                updateRealTimeMode()
            }
        }

        noFilter?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 2
                // Reset threshold only if it was modified by another filter
                if (configuration.detectionThreshold != BigDecimal.ZERO && 
                    configuration.detectionThreshold != BigDecimal.valueOf(5)) {
                    configuration.detectionThreshold = BigDecimal.ZERO
                }
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(noFilter)
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                updateRealTimeMode()
            }
        }

        filterRotation?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 3
                configuration.detectionThreshold = BigDecimal.valueOf(8)
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(filterRotation)
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                updateRealTimeMode()
            }
        }

        butterworthFilter?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 4
                // Reset threshold only if it was modified by another filter
                if (configuration.detectionThreshold != BigDecimal.ZERO && 
                    configuration.detectionThreshold != BigDecimal.valueOf(5)) {
                    configuration.detectionThreshold = BigDecimal.ZERO
                }
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(butterworthFilter)
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                updateRealTimeMode()
            } else {
                updateRealTimeMode()
            }
        }

        falseStepRadio?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.falseStepDetectionEnabled = true
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                noneAdditional?.isChecked = false
                updateRealTimeMode()
            } else {
                configuration.falseStepDetectionEnabled = false
                updateRealTimeMode()
            }
        }

        noneAdditional?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.falseStepDetectionEnabled = false
                falseStepRadio?.isChecked = false
                updateRealTimeMode()
            }
        }

        autocorrelation?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.autocorcAlg = true
                configuration.recognitionAlgorithm = -1
                configuration.filterType = -1
                configuration.falseStepDetectionEnabled = false
                configuration.cutoffFrequencyIndex = -1

                uncheckAllOtherOptionsForAutocorrelation()
                noneAdditional?.isChecked = false
                falseStepRadio?.isChecked = false
                hideCutoffFrequency()
                layoutSamplingRate?.visibility = View.GONE
                updateRealTimeMode()

            } else {
                configuration.autocorcAlg = false
                enableAllOptions()
                if (showSamplingRate) layoutSamplingRate?.visibility = View.VISIBLE
                
                // Reset to defaults when deselecting autocorrelation
                suppressCallbacks = true
                try {
                    applyDefaultSelection()
                } finally {
                    suppressCallbacks = false
                }
                syncUiFromConfiguration()
            }
        }

        cutoffTwo?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) setCutoff(0)
        }
        cutoffThree?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) setCutoff(1)
        }
        cutoffTen?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) setCutoff(2)
        }
        cutoffDividedFifty?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) setCutoff(3)
        }
    }

    private fun syncUiFromConfiguration() {
        suppressCallbacks = true
        try {
            // 1) Reset UI state (clear checks/visibility)
            listOf(samplingTwenty, samplingForty, samplingFifty, samplingHundred, samplingMax)
                .forEach { it?.isChecked = false }
            listOf(recognitionPeak, recognitionIntersection, timeFiltering, autocorrelation)
                .forEach { it?.isChecked = false }
            listOf(filterBagilevi, filterLowPass, noFilter, filterRotation, butterworthFilter)
                .forEach { it?.isChecked = false }
            listOf(cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty)
                .forEach { it?.isChecked = false }
            falseStepRadio?.isChecked = false
            noneAdditional?.isChecked = false
            hideCutoffFrequency()
            if (showSamplingRate) layoutSamplingRate?.visibility = View.VISIBLE

            // 2) Apply sampling
            when (configuration.samplingFrequencyIndex) {
                0 -> samplingTwenty?.isChecked = true
                1 -> samplingForty?.isChecked = true
                2 -> samplingFifty?.isChecked = true
                3 -> samplingHundred?.isChecked = true
                4 -> samplingMax?.isChecked = true
            }

            // 3) Autocorrelation short-circuit
            if (configuration.autocorcAlg) {
                autocorrelation?.isChecked = true
                uncheckAllOtherOptionsForAutocorrelation()
                hideCutoffFrequency()
                layoutSamplingRate?.visibility = View.GONE
                updateRealTimeMode()
                return
            } else {
                autocorrelation?.isChecked = false
                enableAllOptions()
                if (showSamplingRate) layoutSamplingRate?.visibility = View.VISIBLE
            }

            // 4) Recognition
            when (configuration.recognitionAlgorithm) {
                0 -> recognitionPeak?.isChecked = true
                1 -> recognitionIntersection?.isChecked = true
                2 -> timeFiltering?.isChecked = true
            }

            // 5) Filters
            when (configuration.filterType) {
                0 -> { filterBagilevi?.isChecked = true; hideCutoffFrequency() }
                1 -> { filterLowPass?.isChecked = true; showCutoffFrequency() }
                2 -> { noFilter?.isChecked = true; hideCutoffFrequency() }
                3 -> { filterRotation?.isChecked = true; hideCutoffFrequency() }
                4 -> { butterworthFilter?.isChecked = true; hideCutoffFrequency() }
            }

            // 6) Cutoff
            when (configuration.cutoffFrequencyIndex) {
                0 -> cutoffTwo?.isChecked = true
                1 -> cutoffThree?.isChecked = true
                2 -> cutoffTen?.isChecked = true
                3 -> cutoffDividedFifty?.isChecked = true
            }

            // 7) False-step
            falseStepRadio?.isChecked = configuration.falseStepDetectionEnabled
            
            // 8) None 
            if (!configuration.falseStepDetectionEnabled) {
                noneAdditional?.isChecked = true
            }

            // 9) Update real-time mode based on selections
            updateRealTimeMode()

        } finally {
            suppressCallbacks = false
        }
    }

    private fun uncheckAllSamplingExcept(checked: RadioButton?) {
        listOf(samplingTwenty, samplingForty, samplingFifty, samplingHundred, samplingMax)
            .filter { it != checked }
            .forEach { it?.isChecked = false }
    }

    private fun uncheckAllFiltersExcept(checked: RadioButton?) {
        listOf(
            filterBagilevi, filterLowPass, noFilter, filterRotation,
            butterworthFilter, falseStepRadio, autocorrelation
        ).filter { it != checked }.forEach { it?.isChecked = false }
    }

    private fun uncheckAllFiltersExceptKeepingFalseStep(checked: RadioButton?) {
        val shouldKeepFalseStep = configuration.realTimeMode == 1 && 
            (falseStepRadio?.isChecked == true || configuration.falseStepDetectionEnabled)
        
        listOf(
            filterBagilevi, filterLowPass, noFilter, filterRotation,
            butterworthFilter, autocorrelation
        ).filter { it != checked }.forEach { it?.isChecked = false }

        if (shouldKeepFalseStep && checked != falseStepRadio) {
            // Mantieni false step selezionata in modalità non real-time
            // Butterworth filter è compatibile con false step detection
        } else if (checked != falseStepRadio && checked != butterworthFilter) {
            // Deseleziona false step SOLO se non è Butterworth filter
            falseStepRadio?.isChecked = false
        }
    }

    private fun uncheckAllOtherOptionsForAutocorrelation() {
        // Uncheck filters and false-step (but keep enabled)
        listOf(filterBagilevi, filterLowPass, noFilter, filterRotation, butterworthFilter, falseStepRadio, noneAdditional)
            .forEach {
                it?.isChecked = false
            }

        // Uncheck recognition algorithms (but keep enabled)
        listOf(recognitionPeak, recognitionIntersection, timeFiltering)
            .forEach {
                it?.isChecked = false
            }

        // Uncheck cutoff options (but keep enabled)
        listOf(cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty)
            .forEach {
                it?.isChecked = false
            }
    }

    private fun enableAllOptions() {
        listOf(filterBagilevi, filterLowPass, noFilter, filterRotation, butterworthFilter)
            .forEach {
                it?.isEnabled = true
                it?.alpha = 1.0f
            }

        // Recognition algorithms should always be enabled
        listOf(recognitionPeak, recognitionIntersection, timeFiltering)
            .forEach {
                it?.isEnabled = true
                it?.alpha = 1.0f
            }

        listOf(cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty)
            .forEach {
                it?.isEnabled = true
                it?.alpha = 1.0f
            }

        listOf(samplingTwenty, samplingForty, samplingFifty, samplingHundred, samplingMax)
            .forEach {
                it?.isEnabled = true
                it?.alpha = 1.0f
            }

        if (showSamplingRate) {
            layoutSamplingRate?.visibility = View.VISIBLE
        }
        
        // Update availability based on current mode
        updateOptionsAvailability()
    }

    private fun showCutoffFrequency() {
        cutoffFrequencyLayout?.visibility = View.VISIBLE
    }

    private fun hideCutoffFrequency() {
        cutoffFrequencyLayout?.visibility = View.GONE
    }

    private fun setCutoff(index: Int) {
        configuration.cutoffFrequencyIndex = index
        listOf(cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty)
            .filterIndexed { i, _ -> i != index }
            .forEach { it?.isChecked = false }
    }

    private fun applyDefaultSelection() {
        // default UI selection
        suppressCallbacks = true
        try {
            samplingMax?.isChecked = true
            recognitionIntersection?.isChecked = true
            filterLowPass?.isChecked = true
            cutoffDividedFifty?.isChecked = true
            noneAdditional?.isChecked = true
            falseStepRadio?.isChecked = false
        } finally {
            suppressCallbacks = false
        }

        // default configuration values
        applyDefaultConfigurationValues()

        // Update real-time mode based on defaults
        updateRealTimeMode()
    }

    private fun applyDefaultConfigurationValues() {
        configuration.samplingFrequencyIndex = 4  // max sampling
        configuration.realTimeMode = 0           // real-time
        configuration.recognitionAlgorithm = 1   // intersection
        configuration.filterType = 1             // low pass
        configuration.cutoffFrequencyIndex = 3   // divided fifty
        configuration.falseStepDetectionEnabled = false
        configuration.autocorcAlg = false
    }

    fun disableAllViews() {
        scrollView?.setOnTouchListener { _, _ -> true }

        listOf(
            samplingTwenty, samplingForty, samplingFifty, samplingHundred, samplingMax,
            recognitionPeak, recognitionIntersection,
            filterBagilevi, filterLowPass, noFilter, filterRotation,
            cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty,
            butterworthFilter, falseStepRadio, noneAdditional, timeFiltering, autocorrelation
        ).forEach {
            it?.isEnabled = false
            it?.alpha = 0.5f
        }

        cutoffFrequencyLayout?.isEnabled = false
        layoutSamplingRate?.isEnabled = false
        textSamplingRate?.isEnabled = false
        firstView?.isEnabled = false
        currentModalityIndicator?.isEnabled = false
        currentModalityIndicator?.alpha = 0.5f
    }
}
