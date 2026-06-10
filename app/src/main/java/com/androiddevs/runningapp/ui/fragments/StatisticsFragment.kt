package com.androiddevs.runningapp.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView // 🌟 NUEVO
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.ui.CustomMarkerView
import com.androiddevs.runningapp.other.TrackingUtility
import com.androiddevs.runningapp.ui.StatisticsViewModel
import com.github.mikephil.charting.charts.BarChart // 🌟 NUEVO
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import dagger.hilt.android.AndroidEntryPoint
// 🌟 CORREGIDO: Se eliminó el import obsoleto de Synthetics
import kotlin.math.round

@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private val viewModel: StatisticsViewModel by viewModels()

    // 🌟 NUEVO: Referencias globales de las vistas del layout para mantener tus métodos limpios
    private lateinit var barChart: BarChart
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvAverageSpeed: TextView
    private lateinit var tvTotalCalories: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🌟 CORREGIDO: Vinculación manual usando la raíz 'view'
        barChart = view.findViewById(R.id.barChart)
        tvTotalDistance = view.findViewById(R.id.tvTotalDistance)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        tvAverageSpeed = view.findViewById(R.id.tvAverageSpeed)
        tvTotalCalories = view.findViewById(R.id.tvTotalCalories)

        setupLineChart()
        subscribeToObservers()
    }

    private fun setupLineChart() {
        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawLabels(false)
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            setDrawGridLines(false)
        }
        barChart.axisLeft.apply {
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            setDrawGridLines(false)
        }
        barChart.axisRight.apply {
            axisLineColor = Color.WHITE
            textColor = Color.WHITE
            setDrawGridLines(false)
        }
        barChart.apply {
            description.text = "Avg Speed Over Time"
            legend.isEnabled = false
        }
    }

    private fun subscribeToObservers() {
        viewModel.totalDistance.observe(viewLifecycleOwner, Observer {
            // in case DB is empty it will be null
            it?.let {
                val km = it / 1000f
                val totalDistance = round(km * 10) / 10f
                val totalDistanceString = "${totalDistance}km"
                tvTotalDistance.text = totalDistanceString
            }
        })

        viewModel.totalTimeInMillis.observe(viewLifecycleOwner, Observer {
            it?.let {
                val totalTimeInMillis = TrackingUtility.getFormattedStopWatchTime(it)
                tvTotalTime.text = totalTimeInMillis
            }
        })

        viewModel.totalAvgSpeed.observe(viewLifecycleOwner, Observer {
            it?.let {
                val roundedAvgSpeed = round(it * 10f) / 10f
                val totalAvgSpeed = "${roundedAvgSpeed}km/h"
                tvAverageSpeed.text = totalAvgSpeed
            }
        })

        viewModel.totalCaloriesBurned.observe(viewLifecycleOwner, Observer {
            it?.let {
                val totalCaloriesBurned = "${it}kcal"
                tvTotalCalories.text = totalCaloriesBurned
            }
        })

        viewModel.runsSortedByDate.observe(viewLifecycleOwner, Observer {
            it?.let {
                val allAvgSpeeds = it.indices.map { i -> BarEntry(i.toFloat(), it[i].avgSpeedInKMH) }

                val bardataSet = BarDataSet(allAvgSpeeds, "Avg Speed over Time")
                bardataSet.apply {
                    valueTextColor = Color.WHITE
                    color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
                }
                val lineData = BarData(bardataSet)
                barChart.data = lineData
                val marker = CustomMarkerView(
                    it.reversed(),
                    requireContext(),
                    R.layout.marker_view
                )
                barChart.marker = marker
                barChart.invalidate()
            }
        })
    }
}