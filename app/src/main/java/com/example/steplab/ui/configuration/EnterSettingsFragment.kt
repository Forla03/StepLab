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

    private var modalityRealTime: RadioButton? = null
    private var modalityNotRealTime: RadioButton? = null

    private var recognitionPeak: RadioButton? = null
    private var recognitionIntersection: RadioButton? = null

    private var filterBagilevi: RadioButton? = null
    private var filterLowPass: RadioButton? = null
    private var noFilter: RadioButton? = null
    private var filterRotation: RadioButton? = null
    private var butterworthFilter: RadioButton? = null
    private var falseStepRadio: RadioButton? = null
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

        modalityRealTime = root.findViewById(R.id.modality_real_time)
        modalityNotRealTime = root.findViewById(R.id.modality_not_real_time)

        recognitionPeak = root.findViewById(R.id.recognition_peak)
        recognitionIntersection = root.findViewById(R.id.recognition_intersection)

        filterBagilevi = root.findViewById(R.id.filter_bagilevi)
        filterLowPass = root.findViewById(R.id.filter_low_pass)
        noFilter = root.findViewById(R.id.no_filter)
        filterRotation = root.findViewById(R.id.filter_rotation)
        butterworthFilter = root.findViewById(R.id.butterworth_filter)
        falseStepRadio = root.findViewById(R.id.false_step_radio)
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

        // Disable autocorrelation in live testing mode
        if (isLiveTesting) {
            autocorrelation?.isEnabled = false
            autocorrelation?.alpha = 0.5f
        }

        setupListeners()

        // Apply defaults only on first creation and only if autocorr is NOT active
        if (savedInstanceState == null && !configuration.autocorcAlg) {
            applyDefaultSelection()
        } else {
            syncUiFromConfiguration()
        }

        return root
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

        modalityRealTime?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.realTimeMode = 0
                modalityNotRealTime?.isChecked = false
                falseStepRadio?.isEnabled = false
                falseStepRadio?.isChecked = false
                autocorrelation?.isEnabled = false
                autocorrelation?.isChecked = false
                configuration.autocorcAlg = false
                enableAllOptions()
            }
        }

        modalityNotRealTime?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.realTimeMode = 1
                modalityRealTime?.isChecked = false
                falseStepRadio?.isEnabled = true
                falseStepRadio?.isChecked = true
                autocorrelation?.isEnabled = true
                if (autocorrelation?.isChecked != true) {
                    enableAllOptions()
                }

                println("=== NON-REAL-TIME MODE SELECTED ===")
                println("realTimeMode: ${configuration.realTimeMode}")
                println("autocorrelation enabled: ${autocorrelation?.isEnabled}")
                println("autocorrelation checked: ${autocorrelation?.isChecked}")
                println("===================================")
            }
        }

        recognitionPeak?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.recognitionAlgorithm = 0
                recognitionIntersection?.isChecked = false
                timeFiltering?.isChecked = false
            }
        }

        recognitionIntersection?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.recognitionAlgorithm = 1
                recognitionPeak?.isChecked = false
                timeFiltering?.isChecked = false
            }
        }

        timeFiltering?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.recognitionAlgorithm = 2
                recognitionPeak?.isChecked = false
                recognitionIntersection?.isChecked = false
            }
        }

        filterBagilevi?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 0
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(filterBagilevi)
            }
        }

        filterLowPass?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 1
                configuration.detectionThreshold = BigDecimal.valueOf(5)
                showCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(filterLowPass)
                if (!first) scrollView?.post { scrollView?.fullScroll(View.FOCUS_DOWN) } else first = false
            }
        }

        noFilter?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 2
                configuration.detectionThreshold = BigDecimal.valueOf(5)
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(noFilter)
            }
        }

        filterRotation?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 3
                configuration.detectionThreshold = BigDecimal.valueOf(8)
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(filterRotation)
            }
        }

        butterworthFilter?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.filterType = 4
                hideCutoffFrequency()
                uncheckAllFiltersExceptKeepingFalseStep(butterworthFilter)
            }
        }

        falseStepRadio?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.falseStepDetectionEnabled = true
                configuration.realTimeMode = 1
                autocorrelation?.isChecked = false
            } else {
                configuration.falseStepDetectionEnabled = false
            }
        }

        autocorrelation?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (isChecked) {
                configuration.autocorcAlg = true
                configuration.realTimeMode = 1
                configuration.recognitionAlgorithm = -1
                configuration.filterType = -1
                configuration.falseStepDetectionEnabled = false
                configuration.cutoffFrequencyIndex = -1

                suppressCallbacks = true
                try {
                    modalityNotRealTime?.isChecked = true
                    modalityRealTime?.isChecked = false
                } finally {
                    suppressCallbacks = false
                }

                disableAllOtherOptionsForAutocorrelation()
                hideCutoffFrequency()
                layoutSamplingRate?.visibility = View.GONE

                println("=== AUTOCORRELATION CONFIGURATION ===")
                println("autocorcAlg: ${configuration.autocorcAlg}")
                println("realTimeMode: ${configuration.realTimeMode} (forced to non-real-time)")
                println("recognitionAlgorithm: ${configuration.recognitionAlgorithm} (disabled for autocorrelation)")
                println("filterType: ${configuration.filterType} (disabled for autocorrelation)")
                println("falseStepDetectionEnabled: ${configuration.falseStepDetectionEnabled}")
                println("samplingFrequencyIndex: ${configuration.samplingFrequencyIndex}")
                println("cutoffFrequencyIndex: ${configuration.cutoffFrequencyIndex} (disabled for autocorrelation)")
                println("detectionThreshold: ${configuration.detectionThreshold}")
                println("===================================")
            } else {
                configuration.autocorcAlg = false
                enableAllOptions()
                if (showSamplingRate) layoutSamplingRate?.visibility = View.VISIBLE
                // Do not reset to defaults here; let the user pick or keep current config
                syncUiFromConfiguration()
                println("=== AUTOCORRELATION DISABLED ===")
                println("autocorcAlg: ${configuration.autocorcAlg}")
                println("================================")
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
        // Reflect current configuration into UI without triggering listeners
        suppressCallbacks = true
        try {
            // Sampling
            when (configuration.samplingFrequencyIndex) {
                0 -> samplingTwenty?.isChecked = true
                1 -> samplingForty?.isChecked = true
                2 -> samplingFifty?.isChecked = true
                3 -> samplingHundred?.isChecked = true
                4 -> samplingMax?.isChecked = true
            }

            // Modality
            if (configuration.realTimeMode == 0) {
                modalityRealTime?.isChecked = true
            } else {
                modalityNotRealTime?.isChecked = true
            }

            if (configuration.autocorcAlg) {
                autocorrelation?.isChecked = true
                disableAllOtherOptionsForAutocorrelation()
                hideCutoffFrequency()
                layoutSamplingRate?.visibility = View.GONE
                return
            } else {
                autocorrelation?.isChecked = false
                enableAllOptions()
                if (showSamplingRate) layoutSamplingRate?.visibility = View.VISIBLE
            }

            // Recognition
            when (configuration.recognitionAlgorithm) {
                0 -> recognitionPeak?.isChecked = true
                1 -> recognitionIntersection?.isChecked = true
                2 -> timeFiltering?.isChecked = true
            }

            // Filters
            when (configuration.filterType) {
                0 -> { filterBagilevi?.isChecked = true; hideCutoffFrequency() }
                1 -> { filterLowPass?.isChecked = true; showCutoffFrequency() }
                2 -> { noFilter?.isChecked = true; hideCutoffFrequency() }
                3 -> { filterRotation?.isChecked = true; hideCutoffFrequency() }
                4 -> { butterworthFilter?.isChecked = true; hideCutoffFrequency() }
            }

            // Cutoff
            when (configuration.cutoffFrequencyIndex) {
                0 -> cutoffTwo?.isChecked = true
                1 -> cutoffThree?.isChecked = true
                2 -> cutoffTen?.isChecked = true
                3 -> cutoffDividedFifty?.isChecked = true
            }

            // False step
            falseStepRadio?.isChecked = configuration.falseStepDetectionEnabled

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
        val shouldKeepFalseStep = configuration.realTimeMode == 1 && falseStepRadio?.isChecked == true
        listOf(
            filterBagilevi, filterLowPass, noFilter, filterRotation,
            butterworthFilter, autocorrelation
        ).filter { it != checked }.forEach { it?.isChecked = false }

        if (shouldKeepFalseStep && checked != falseStepRadio) {
            // keep false step checked in non real-time
        } else if (checked != falseStepRadio) {
            falseStepRadio?.isChecked = false
        }
    }

    private fun disableAllOtherOptionsForAutocorrelation() {
        // Force non-real-time modality disabled state
        modalityRealTime?.let {
            it.isEnabled = false
            it.alpha = 0.5f
        }

        // Disable filters and false-step
        listOf(filterBagilevi, filterLowPass, noFilter, filterRotation, butterworthFilter, falseStepRadio)
            .forEach {
                it?.isChecked = false
                it?.isEnabled = false
                it?.alpha = 0.5f
            }

        // Disable recognition algos
        listOf(recognitionPeak, recognitionIntersection, timeFiltering)
            .forEach {
                it?.isChecked = false
                it?.isEnabled = false
                it?.alpha = 0.5f
            }

        // Disable cutoff
        listOf(cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty)
            .forEach {
                it?.isChecked = false
                it?.isEnabled = false
                it?.alpha = 0.5f
            }

        // Disable sampling radios
        listOf(samplingTwenty, samplingForty, samplingFifty, samplingHundred, samplingMax)
            .forEach {
                it?.isEnabled = false
                it?.alpha = 0.5f
            }
    }

    private fun enableAllOptions() {
        modalityRealTime?.let {
            it.isEnabled = true
            it.alpha = 1.0f
        }
        modalityNotRealTime?.let {
            it.isEnabled = true
            it.alpha = 1.0f
        }

        listOf(filterBagilevi, filterLowPass, noFilter, filterRotation, butterworthFilter)
            .forEach {
                it?.isEnabled = true
                it?.alpha = 1.0f
            }

        falseStepRadio?.let { radio ->
            radio.isEnabled = configuration.realTimeMode == 1
            radio.alpha = if (configuration.realTimeMode == 1) 1.0f else 0.5f
        }

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
            modalityRealTime?.isChecked = true
            recognitionIntersection?.isChecked = true
            filterLowPass?.isChecked = true
            cutoffDividedFifty?.isChecked = true
        } finally {
            suppressCallbacks = false
        }

        // default configuration values
        applyDefaultConfigurationValues()

        println("=== DEFAULT CONFIGURATION APPLIED ===")
        println("samplingFrequencyIndex: ${configuration.samplingFrequencyIndex}")
        println("realTimeMode: ${configuration.realTimeMode}")
        println("recognitionAlgorithm: ${configuration.recognitionAlgorithm}")
        println("filterType: ${configuration.filterType}")
        println("cutoffFrequencyIndex: ${configuration.cutoffFrequencyIndex}")
        println("autocorcAlg: ${configuration.autocorcAlg}")
        println("falseStepDetectionEnabled: ${configuration.falseStepDetectionEnabled}")
        println("=====================================")
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
            modalityRealTime, modalityNotRealTime,
            recognitionPeak, recognitionIntersection,
            filterBagilevi, filterLowPass, noFilter, filterRotation,
            cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty,
            butterworthFilter, falseStepRadio, timeFiltering, autocorrelation
        ).forEach {
            it?.isEnabled = false
            it?.alpha = 0.5f
        }

        cutoffFrequencyLayout?.isEnabled = false
        layoutSamplingRate?.isEnabled = false
        textSamplingRate?.isEnabled = false
        firstView?.isEnabled = false
    }
}
