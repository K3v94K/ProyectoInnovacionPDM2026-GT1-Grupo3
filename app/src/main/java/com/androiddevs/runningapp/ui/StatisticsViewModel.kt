package com.androiddevs.runningapp.ui

import androidx.lifecycle.ViewModel
import com.androiddevs.runningapp.repositories.MainRepository
import dagger.hilt.android.lifecycle.HiltViewModel // 🌟 NUEVO: Importación moderna de Hilt
import javax.inject.Inject // 🌟 NUEVO: Usamos el inyector estándar

@HiltViewModel // 🌟 CORREGIDO: Anotación moderna de Hilt para ViewModels
class StatisticsViewModel @Inject constructor( // 🌟 CORREGIDO: Sintaxis estándar @Inject constructor
    mainRepository: MainRepository
) : ViewModel() {

    var totalDistance = mainRepository.getTotalDistance()
    var totalTimeInMillis = mainRepository.getTotalTimeInMillis()
    var totalAvgSpeed = mainRepository.getTotalAvgSpeed()
    var totalCaloriesBurned = mainRepository.getTotalCaloriesBurned()

    var runsSortedByDate = mainRepository.getAllRunsSortedByDate()
}