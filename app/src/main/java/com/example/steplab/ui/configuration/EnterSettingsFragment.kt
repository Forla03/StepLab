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
    private val showSamplingRate: Boolean = true
) : Fragment() {

    private var first = true

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
        }

        setupListeners()
        applyDefaultSelection()

        return root
    }

    private fun setupListeners() {
        samplingTwenty?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.samplingFrequencyIndex = 0
                uncheckAllSamplingExcept(samplingTwenty)
            }
        }
        samplingForty?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.samplingFrequencyIndex = 1
                uncheckAllSamplingExcept(samplingForty)
            }
        }
        samplingFifty?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.samplingFrequencyIndex = 2
                uncheckAllSamplingExcept(samplingFifty)
            }
        }
        samplingHundred?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.samplingFrequencyIndex = 3
                uncheckAllSamplingExcept(samplingHundred)
            }
        }
        samplingMax?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.samplingFrequencyIndex = 4
                uncheckAllSamplingExcept(samplingMax)
            }
        }

        modalityRealTime?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.realTimeMode = 0
                modalityNotRealTime?.isChecked = false
            }
        }

        modalityNotRealTime?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.realTimeMode = 1
                modalityRealTime?.isChecked = false
            }
        }

        recognitionPeak?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.recognitionAlgorithm = 0
                recognitionIntersection?.isChecked = false
            }
        }

        recognitionIntersection?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.recognitionAlgorithm = 1
                recognitionPeak?.isChecked = false
            }
        }

        timeFiltering?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.recognitionAlgorithm = 2
                recognitionPeak?.isChecked = false
                recognitionIntersection?.isChecked = false
            }
        }

        filterBagilevi?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.filterType = 0
                hideCutoffFrequency()
                uncheckAllFiltersExcept(filterBagilevi)
            }
        }

        filterLowPass?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.filterType = 1
                configuration.detectionThreshold = BigDecimal.valueOf(5)
                showCutoffFrequency()
                uncheckAllFiltersExcept(filterLowPass)
                if (!first) scrollView?.post { scrollView?.fullScroll(View.FOCUS_DOWN) } else first = false
            }
        }

        noFilter?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.filterType = 2
                configuration.detectionThreshold = BigDecimal.valueOf(5)
                hideCutoffFrequency()
                uncheckAllFiltersExcept(noFilter)
            }
        }

        filterRotation?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.filterType = 3
                configuration.detectionThreshold = BigDecimal.valueOf(8)
                hideCutoffFrequency()
                uncheckAllFiltersExcept(filterRotation)
            }
        }

        butterworthFilter?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.filterType = 4
                hideCutoffFrequency()
                uncheckAllFiltersExcept(butterworthFilter)
                recognitionPeak?.isChecked = false
            }
        }

        falseStepRadio?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.filterType = 4
                autocorrelation?.isChecked = false
                hideCutoffFrequency()
                uncheckAllFiltersExcept(falseStepRadio)
                recognitionPeak?.isChecked = false
            }
        }

        autocorrelation?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                configuration.autocorcAlg = true
                falseStepRadio?.isChecked = false
                hideCutoffFrequency()
                layoutSamplingRate?.visibility = View.GONE
                uncheckAllFiltersExcept(autocorrelation)
                recognitionPeak?.isChecked = false
            }
        }

        cutoffTwo?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) setCutoff(0)
        }
        cutoffThree?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) setCutoff(1)
        }
        cutoffTen?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) setCutoff(2)
        }
        cutoffDividedFifty?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) setCutoff(3)
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
        samplingMax?.isChecked = true
        modalityRealTime?.isChecked = true
        recognitionIntersection?.isChecked = true
        filterLowPass?.isChecked = true
        cutoffDividedFifty?.isChecked = true
    }

    fun disableAllViews() {
        scrollView?.setOnTouchListener { _, _ -> true }

        listOf(
            samplingTwenty, samplingForty, samplingFifty, samplingHundred, samplingMax,
            modalityRealTime, modalityNotRealTime,
            recognitionPeak, recognitionIntersection,
            filterBagilevi, filterLowPass, noFilter, filterRotation,
            cutoffTwo, cutoffThree, cutoffTen, cutoffDividedFifty,
            cutoffFrequencyLayout, layoutSamplingRate, textSamplingRate, firstView,
            butterworthFilter, falseStepRadio, timeFiltering, autocorrelation
        ).forEach { it?.isEnabled = false }
    }
}
