package com.androiddevs.runningapp.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.TrackingUtility
import com.androiddevs.runningapp.ui.CustomMarkerView
import com.androiddevs.runningapp.ui.StatisticsViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private val viewModel: StatisticsViewModel by viewModels()

    private lateinit var barChart: BarChart
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvAverageSpeed: TextView
    private lateinit var tvTotalCalories: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barChart = view.findViewById(R.id.barChart)
        tvTotalDistance = view.findViewById(R.id.tvTotalDistance)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        tvAverageSpeed = view.findViewById(R.id.tvAverageSpeed)
        tvTotalCalories = view.findViewById(R.id.tvTotalCalories)

        setupBarChart()
        subscribeToObservers()
    }

    private fun setupBarChart() {
        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawLabels(true)
            setDrawGridLines(false)
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            granularity = 1f
            labelRotationAngle = -35f
        }

        barChart.axisLeft.apply {
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            gridColor = Color.argb(70, 255, 255, 255)
            axisMinimum = 0f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format(Locale.getDefault(), "%.1f", value)
                }
            }
        }

        barChart.axisRight.isEnabled = false

        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setNoDataText(getString(R.string.stats_no_runs_chart))
            setNoDataTextColor(Color.WHITE)
            extraBottomOffset = 8f
        }
    }

    private fun subscribeToObservers() {
        viewModel.totalDistance.observe(viewLifecycleOwner, Observer {
            it?.let {
                val km = it / 1000f
                val totalDistance = round(km * 10) / 10f
                tvTotalDistance.text = String.format(Locale.getDefault(), "%.1f km", totalDistance)
            }
        })

        viewModel.totalTimeInMillis.observe(viewLifecycleOwner, Observer {
            it?.let {
                tvTotalTime.text = TrackingUtility.getFormattedStopWatchTime(it)
            }
        })

        viewModel.totalAvgSpeed.observe(viewLifecycleOwner, Observer {
            it?.let {
                val roundedAvgSpeed = round(it * 10f) / 10f
                tvAverageSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", roundedAvgSpeed)
            }
        })

        viewModel.totalCaloriesBurned.observe(viewLifecycleOwner, Observer {
            it?.let {
                tvTotalCalories.text = getString(R.string.live_calories_value, it.toInt())
            }
        })

        viewModel.runsSortedByDate.observe(viewLifecycleOwner, Observer { runs ->
            if (runs.isNullOrEmpty()) {
                barChart.clear()
                barChart.invalidate()
                return@Observer
            }

            val runsInChronologicalOrder = runs.reversed()
            val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            val labels = runsInChronologicalOrder.map { run ->
                dateFormat.format(Date(run.timestamp))
            }
            val avgSpeedEntries = runsInChronologicalOrder.indices.map { i ->
                BarEntry(i.toFloat(), runsInChronologicalOrder[i].avgSpeedInKMH)
            }

            val dataSet = BarDataSet(avgSpeedEntries, getString(R.string.stats_chart_description)).apply {
                color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
                valueTextColor = Color.WHITE
                valueTextSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format(Locale.getDefault(), "%.1f km/h", value)
                    }
                }
            }

            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            barChart.xAxis.labelCount = labels.size.coerceAtMost(5)
            barChart.data = BarData(dataSet).apply {
                barWidth = 0.55f
            }
            barChart.marker = CustomMarkerView(
                runsInChronologicalOrder,
                requireContext(),
                R.layout.marker_view
            )
            barChart.animateY(600)
            barChart.invalidate()
        })
    }
}
