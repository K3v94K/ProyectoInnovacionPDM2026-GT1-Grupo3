package com.androiddevs.runningapp.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.db.Run
import com.androiddevs.runningapp.db.RunPoint
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_PAUSE_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_START_OR_RESUME_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_STOP_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.EXTRA_GOAL_TYPE
import com.androiddevs.runningapp.other.Constants.Companion.EXTRA_GOAL_VALUE
import com.androiddevs.runningapp.other.Constants.Companion.GOAL_REACHED_NOTIFICATION_ID
import com.androiddevs.runningapp.other.Constants.Companion.MAP_VIEW_BUNDLE_KEY
import com.androiddevs.runningapp.other.Constants.Companion.MAP_ZOOM
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_CHANNEL_ID
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_CHANNEL_NAME
import com.androiddevs.runningapp.other.Constants.Companion.POLYLINE_COLOR
import com.androiddevs.runningapp.other.Constants.Companion.POLYLINE_WIDTH
import com.androiddevs.runningapp.other.TrackingUtility
import com.androiddevs.runningapp.services.TrackingService
import com.androiddevs.runningapp.ui.MainViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import kotlin.math.round

const val CANCEL_DIALOG_TAG = "CancelDialog"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking) {

    private enum class GoalType {
        DISTANCE,
        TIME,
        CALORIES
    }

    private data class TrainingGoal(
        val type: GoalType,
        val targetValue: Float
    )

    @set:Inject
    var weight: Float = 80f

    private var map: GoogleMap? = null

    private lateinit var mapView: MapView
    private lateinit var btnToggleRun: Button
    private lateinit var btnFinishRun: Button
    private lateinit var tvTimer: TextView
    private lateinit var tvLiveDistance: TextView
    private lateinit var tvLiveAvgSpeed: TextView
    private lateinit var tvLiveCalories: TextView
    private lateinit var llGoalSetup: LinearLayout
    private lateinit var rgGoalType: RadioGroup
    private lateinit var etGoalValue: EditText
    private lateinit var goalProgressBar: ProgressBar
    private lateinit var tvGoalProgress: TextView
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var isTracking = false
    private var curTimeInMillis = 0L
    private var pathPoints = mutableListOf<MutableList<LatLng>>()
    private var activeGoal: TrainingGoal? = null
    private var wasGoalReached = false
    private var isSavingRun = false
    private var hasCenteredInitialLocation = false

    private val viewModel: MainViewModel by viewModels()

    private var menu: Menu? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)
        btnToggleRun = view.findViewById(R.id.btnToggleRun)
        btnFinishRun = view.findViewById(R.id.btnFinishRun)
        tvTimer = view.findViewById(R.id.tvTimer)
        tvLiveDistance = view.findViewById(R.id.tvLiveDistance)
        tvLiveAvgSpeed = view.findViewById(R.id.tvLiveAvgSpeed)
        tvLiveCalories = view.findViewById(R.id.tvLiveCalories)
        llGoalSetup = view.findViewById(R.id.llGoalSetup)
        rgGoalType = view.findViewById(R.id.rgGoalType)
        etGoalValue = view.findViewById(R.id.etGoalValue)
        goalProgressBar = view.findViewById(R.id.goalProgressBar)
        tvGoalProgress = view.findViewById(R.id.tvGoalProgress)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val mapViewBundle = savedInstanceState?.getBundle(MAP_VIEW_BUNDLE_KEY)
        mapView.onCreate(mapViewBundle)
        setupGoalInputBehavior()

        if(savedInstanceState != null) {
            val cancelRunDialog = parentFragmentManager.findFragmentByTag(CANCEL_DIALOG_TAG) as CancelRunDialog?
            cancelRunDialog?.setYesListener {
                stopRun()
            }
        }

        btnToggleRun.setOnClickListener {
            toggleRun()
        }

        btnFinishRun.setOnClickListener {
            if (!canFinishRun()) {
                return@setOnClickListener
            }
            zoomToWholeTrack()
            mapView.post {
                endRunAndSaveToDB()
            }
        }

        mapView.getMapAsync { googleMap ->
            map = googleMap
            // 🌟 NUEVO: Habilita el punto azul de GPS y el botón de centrado nativo en tu teléfono
            try {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
                centerMapOnCurrentLocation()
            } catch (e: SecurityException) {
                Timber.e(e, "Faltan permisos de ubicación para mostrar el punto azul")
            }
            addAllPolylines()
            TrackingService.currentLocation.value?.let { location ->
                moveCameraToLocation(location)
            }
        }
        subscribeToObservers()
    }

    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()
            updateLiveRunStats()
        })

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(it, true)
            tvTimer.text = formattedTime
            updateLiveRunStats()
        })

        TrackingService.currentLocation.observe(viewLifecycleOwner, Observer { location ->
            if (location != null && isTracking && !hasCenteredInitialLocation) {
                hasCenteredInitialLocation = true
                moveCameraToLocation(location)
            }
        })

        TrackingService.goalReached.observe(viewLifecycleOwner, Observer { goalReached ->
            if (goalReached == true) {
                wasGoalReached = true
            }
        })
    }

    /**
     * Actualiza las metricas principales durante el recorrido para cumplir el monitoreo
     * deportivo en tiempo real solicitado por el tema del proyecto.
     */
    private fun updateLiveRunStats() {
        val distanceInMeters = calculateCurrentDistanceInMeters()
        val distanceInKm = distanceInMeters / 1000f
        val avgSpeed = calculateAvgSpeedInKmh(distanceInMeters, curTimeInMillis)
        val caloriesBurned = calculateCaloriesBurned(distanceInMeters)

        tvLiveDistance.text = String.format(Locale.getDefault(), "%.2f km", distanceInKm)
        tvLiveAvgSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", avgSpeed)
        tvLiveCalories.text = getString(R.string.live_calories_value, caloriesBurned)
        updateGoalProgress(distanceInMeters, caloriesBurned)
    }

    private fun resetLiveRunStats() {
        tvLiveDistance.text = getString(R.string.live_distance_placeholder)
        tvLiveAvgSpeed.text = getString(R.string.live_speed_placeholder)
        tvLiveCalories.text = getString(R.string.live_calories_placeholder)
        goalProgressBar.progress = 0
        tvGoalProgress.text = getString(R.string.goal_progress_empty)
    }

    private fun setupGoalInputBehavior() {
        rgGoalType.setOnCheckedChangeListener { _, _ ->
            updateGoalInputMode()
        }
        etGoalValue.setOnClickListener {
            if (rgGoalType.checkedRadioButtonId == R.id.rbGoalTime) {
                showTimeGoalPicker()
            }
        }
        updateGoalInputMode()
    }

    private fun updateGoalInputMode() {
        when (rgGoalType.checkedRadioButtonId) {
            R.id.rbGoalTime -> {
                etGoalValue.hint = getString(R.string.goal_time_value_hint)
                etGoalValue.inputType = InputType.TYPE_NULL
                etGoalValue.isFocusable = false
                etGoalValue.isClickable = true
                etGoalValue.isCursorVisible = false
                etGoalValue.tag = null
                etGoalValue.setText("")
            }
            R.id.rbGoalCalories -> {
                etGoalValue.hint = getString(R.string.goal_calories_value_hint)
                etGoalValue.inputType = InputType.TYPE_CLASS_NUMBER
                etGoalValue.isFocusable = true
                etGoalValue.isClickable = true
                etGoalValue.isFocusableInTouchMode = true
                etGoalValue.isCursorVisible = true
                etGoalValue.tag = null
                etGoalValue.setText("")
            }
            else -> {
                etGoalValue.hint = getString(R.string.goal_distance_value_hint)
                etGoalValue.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                etGoalValue.isFocusable = true
                etGoalValue.isClickable = true
                etGoalValue.isFocusableInTouchMode = true
                etGoalValue.isCursorVisible = true
                etGoalValue.tag = null
                etGoalValue.setText("")
            }
        }
    }

    private fun showTimeGoalPicker() {
        val currentMinutes = (etGoalValue.tag as? Int) ?: 30
        val hourPicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 23
            value = currentMinutes / 60
        }
        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 59
            value = currentMinutes % 60
        }
        val pickerContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 8, 24, 0)
            addView(hourPicker)
            addView(minutePicker)
        }

        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.goal_time_picker_title))
            .setView(pickerContainer)
            .setPositiveButton(getString(R.string.accept)) { _, _ ->
                val totalMinutes = (hourPicker.value * 60 + minutePicker.value).coerceAtLeast(1)
                etGoalValue.tag = totalMinutes
                etGoalValue.setText(formatTimeGoalInput(totalMinutes))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun readGoalFromInput(): TrainingGoal? {
        val targetValue = if (rgGoalType.checkedRadioButtonId == R.id.rbGoalTime) {
            (etGoalValue.tag as? Int)?.toFloat()
        } else {
            etGoalValue.text.toString().toFloatOrNull()
        }
        if (targetValue == null || targetValue <= 0f) {
            return null
        }

        val goalType = when (rgGoalType.checkedRadioButtonId) {
            R.id.rbGoalTime -> GoalType.TIME
            R.id.rbGoalCalories -> GoalType.CALORIES
            else -> GoalType.DISTANCE
        }
        return TrainingGoal(goalType, targetValue)
    }

    private fun formatTimeGoalInput(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> getString(R.string.goal_time_hours_minutes, hours, minutes)
            hours > 0 -> getString(R.string.goal_time_hours, hours)
            else -> getString(R.string.goal_time_minutes, minutes)
        }
    }

    private fun updateGoalProgress(distanceInMeters: Int, caloriesBurned: Int) {
        val goal = activeGoal ?: return
        val currentValue = when (goal.type) {
            GoalType.DISTANCE -> distanceInMeters / 1000f
            GoalType.TIME -> curTimeInMillis / 1000f / 60f
            GoalType.CALORIES -> caloriesBurned.toFloat()
        }
        val progressPercent = ((currentValue / goal.targetValue) * 100f).toInt().coerceIn(0, 100)
        goalProgressBar.progress = progressPercent

        val goalName = getGoalName(goal.type)
        tvGoalProgress.text = if (progressPercent >= 100) {
            if (!wasGoalReached) {
                wasGoalReached = true
            }
            getString(R.string.goal_reached_format, goalName)
        } else {
            getString(
                R.string.goal_progress_format,
                goalName,
                formatGoalValue(goal.type, currentValue),
                formatGoalValue(goal.type, goal.targetValue),
                progressPercent
            )
        }
    }

    private fun showGoalReachedNotification(goalName: String) {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Notificacion local: avisa aunque el usuario no este mirando la pantalla.
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
            .setContentTitle(getString(R.string.goal_notification_title))
            .setContentText(getString(R.string.goal_reached_format, goalName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(GOAL_REACHED_NOTIFICATION_ID, notification)
    }

    private fun getGoalName(goalType: GoalType): String {
        return when (goalType) {
            GoalType.DISTANCE -> getString(R.string.goal_type_distance)
            GoalType.TIME -> getString(R.string.goal_type_time)
            GoalType.CALORIES -> getString(R.string.goal_type_calories)
        }
    }

    private fun formatGoalValue(goalType: GoalType, value: Float): String {
        return when (goalType) {
            GoalType.DISTANCE -> String.format(Locale.getDefault(), "%.2f km", value)
            GoalType.TIME -> String.format(Locale.getDefault(), "%.1f min", value)
            GoalType.CALORIES -> getString(R.string.live_calories_value, value.toInt())
        }
    }

    private fun calculateCurrentDistanceInMeters(): Int {
        var distanceInMeters = 0
        for (polyline in pathPoints) {
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
        }
        return distanceInMeters
    }

    private fun calculateAvgSpeedInKmh(distanceInMeters: Int, timeInMillis: Long): Float {
        if (distanceInMeters <= 0 || timeInMillis <= 0L) return 0f
        val hours = timeInMillis / 1000f / 60f / 60f
        return round((distanceInMeters / 1000f) / hours * 10f) / 10f
    }

    private fun calculateCaloriesBurned(distanceInMeters: Int): Int {
        return ((distanceInMeters / 1000f) * weight).toInt()
    }

    private fun moveCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            moveCameraToLocation(pathPoints.last().last())
        }
    }

    private fun moveCameraToLocation(location: LatLng) {
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                location,
                MAP_ZOOM
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnCurrentLocation() {
        if (!TrackingUtility.hasLocationPermissions(requireContext())) {
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationProviderClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            location?.let {
                hasCenteredInitialLocation = true
                moveCameraToLocation(LatLng(it.latitude, it.longitude))
            } ?: centerMapOnLastKnownLocation()
        }.addOnFailureListener {
            centerMapOnLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnLastKnownLocation() {
        if (!TrackingUtility.hasLocationPermissions(requireContext())) {
            return
        }

        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                hasCenteredInitialLocation = true
                moveCameraToLocation(LatLng(it.latitude, it.longitude))
            }
        }
    }

    private fun addAllPolylines() {
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polylineOptions)
        }
    }

    private fun addLatestPolyline() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)

            map?.addPolyline(polylineOptions)
        }
    }

    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if (!isTracking && curTimeInMillis > 0L) {
            btnToggleRun.text = getString(R.string.start_text)
            btnFinishRun.visibility = View.VISIBLE
            llGoalSetup.visibility = View.GONE
        } else if (isTracking) {
            btnToggleRun.text = getString(R.string.stop_text)
            menu?.getItem(0)?.isVisible = true
            btnFinishRun.visibility = View.GONE
            llGoalSetup.visibility = View.GONE
        } else {
            llGoalSetup.visibility = View.VISIBLE
        }
    }

    @SuppressLint("MissingPermission")
    private fun toggleRun() {
        if (isTracking) {
            menu?.getItem(0)?.isVisible = true
            pauseTrackingService()
        } else {
            if (!TrackingUtility.hasLocationPermissions(requireContext())) {
                Snackbar.make(requireView(), getString(R.string.location_permission_required), Snackbar.LENGTH_SHORT).show()
                return
            }
            if (activeGoal == null) {
                activeGoal = readGoalFromInput()
                if (activeGoal == null) {
                    Snackbar.make(requireView(), getString(R.string.goal_invalid_message), Snackbar.LENGTH_SHORT).show()
                    return
                }
                wasGoalReached = false
            }
            hasCenteredInitialLocation = false
            centerMapOnCurrentLocation()
            startOrResumeTrackingService()
            Timber.d("Started service")
        }
    }

    private fun canFinishRun(): Boolean {
        if (isSavingRun) {
            return false
        }
        if (curTimeInMillis < 1000L) {
            Snackbar.make(requireView(), getString(R.string.run_too_short_message), Snackbar.LENGTH_SHORT).show()
            return false
        }
        if (calculateCurrentDistanceInMeters() <= 0) {
            Snackbar.make(requireView(), getString(R.string.run_no_distance_message), Snackbar.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun startOrResumeTrackingService() =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = ACTION_START_OR_RESUME_SERVICE
            activeGoal?.let { goal ->
                it.putExtra(EXTRA_GOAL_TYPE, goal.type.name)
                it.putExtra(EXTRA_GOAL_VALUE, goal.targetValue)
            }
            requireContext().startService(it)
        }

    private fun pauseTrackingService() =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = ACTION_PAUSE_SERVICE
            requireContext().startService(it)
        }

    private fun stopTrackingService() =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = ACTION_STOP_SERVICE
            requireContext().startService(it)
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) {
            val mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY)
            if (mapViewBundle != null) {
                mapView.onSaveInstanceState(mapViewBundle)
            } else {
                val newBundle = Bundle()
                outState.putBundle(MAP_VIEW_BUNDLE_KEY, newBundle)
                mapView.onSaveInstanceState(newBundle)
            }
        }
    }

    private fun zoomToWholeTrack() {
        if (pathPoints.all { it.isEmpty() }) {
            return
        }
        val bounds = LatLngBounds.Builder()
        for (polyline in pathPoints) {
            for (point in polyline) {
                bounds.include(point)
            }
        }
        val width = mapView.width
        val height = mapView.height
        if (width > 0 && height > 0) {
            map?.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds.build(),
                    width,
                    height,
                    (height * 0.05f).toInt()
                )
            )
        }
    }

    private fun endRunAndSaveToDB() {
        isSavingRun = true
        btnFinishRun.isEnabled = false

        if (canTakeMapSnapshot()) {
            try {
                map?.snapshot { bmp ->
                    saveRunToDB(bmp)
                } ?: saveRunToDB(null)
            } catch (e: RuntimeException) {
                Timber.e(e, "No se pudo capturar el mapa; se guarda la ruta sin imagen")
                saveRunToDB(null)
            }
            return
        }

        saveRunToDB(null)
    }

    private fun canTakeMapSnapshot(): Boolean {
        return map != null &&
            ::mapView.isInitialized &&
            mapView.isLaidOut &&
            mapView.isShown &&
            mapView.width > 0 &&
            mapView.height > 0
    }

    private fun saveRunToDB(routeImage: android.graphics.Bitmap?) {
        if (!isAdded) {
            return
        }

        val distanceInMeters = calculateCurrentDistanceInMeters()
        val avgSpeed = calculateAvgSpeedInKmh(distanceInMeters, curTimeInMillis)
        val timestamp = Calendar.getInstance().timeInMillis
        val caloriesBurned = calculateCaloriesBurned(distanceInMeters)
        val goal = activeGoal

        // La imagen del mapa mejora el historial, pero las metricas son la fuente
        // principal del entrenamiento. Si no hay snapshot, la carrera igual se guarda.
        val run =
            Run(
                routeImage,
                timestamp,
                avgSpeed,
                distanceInMeters,
                curTimeInMillis,
                caloriesBurned,
                goal?.type?.name ?: "",
                goal?.targetValue ?: 0f,
                wasGoalReached
            )
        viewModel.insertRunWithPoints(run, buildRunPointsForCurrentRoute())
        showRunSummaryDialog(run)
    }

    private fun buildRunPointsForCurrentRoute(): List<RunPoint> {
        val recordedAt = Calendar.getInstance().timeInMillis
        return pathPoints.flatMapIndexed { segmentIndex, polyline ->
            polyline.mapIndexed { pointIndex, point ->
                RunPoint(
                    runId = 0,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    segmentIndex = segmentIndex,
                    pointIndex = pointIndex,
                    recordedAt = recordedAt + pointIndex
                )
            }
        }
    }

    private fun showRunSummaryDialog(run: Run) {
        if (!isAdded) {
            return
        }
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.run_summary_title))
            .setMessage(buildRunSummaryText(run))
            .setPositiveButton(getString(R.string.run_summary_close)) { _, _ ->
                stopRun()
            }
            .setOnCancelListener {
                stopRun()
            }
            .show()
    }

    private fun buildRunSummaryText(run: Run): String {
        val distance = String.format(Locale.getDefault(), "%.2f km", run.distanceInMeters / 1000f)
        val time = TrackingUtility.getFormattedStopWatchTime(run.timeInMillis)
        val avgSpeed = String.format(Locale.getDefault(), "%.1f km/h", run.avgSpeedInKMH)
        val calories = getString(R.string.live_calories_value, run.caloriesBurned)
        val goal = getRunGoalSummary(run)

        return listOf(
            getString(R.string.run_summary_saved),
            getString(R.string.run_summary_distance, distance),
            getString(R.string.run_summary_time, time),
            getString(R.string.run_summary_avg_speed, avgSpeed),
            getString(R.string.run_summary_calories, calories),
            getString(R.string.run_summary_goal, goal)
        ).joinToString(separator = "\n")
    }

    private fun getRunGoalSummary(run: Run): String {
        if (run.goalType.isBlank() || run.goalValue <= 0f) {
            return getString(R.string.history_goal_none)
        }

        val goalType = runCatching { GoalType.valueOf(run.goalType) }.getOrNull()
            ?: return getString(R.string.history_goal_none)
        val goalName = getGoalName(goalType)
        val goalValue = formatGoalValue(goalType, run.goalValue)
        val result = if (run.goalAchieved) {
            getString(R.string.history_goal_achieved)
        } else {
            getString(R.string.history_goal_pending)
        }
        return "$goalName $goalValue - $result"
    }

    private fun stopRun() {
        Timber.d("STOPPING RUN")
        tvTimer.text = getString(R.string.timer_placeholder)
        resetLiveRunStats()
        activeGoal = null
        wasGoalReached = false
        isSavingRun = false
        hasCenteredInitialLocation = false
        btnFinishRun.isEnabled = true
        llGoalSetup.visibility = View.VISIBLE
        stopTrackingService()
        findNavController().navigate(R.id.action_trackingFragment_to_runFragment2)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.miCancelTracking -> {
                showCancelTrackingDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolbar_menu_tracking, menu)
        this.menu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (curTimeInMillis > 0L) {
            this.menu?.getItem(0)?.isVisible = true
        }
    }

    private fun showCancelTrackingDialog() {
        CancelRunDialog().apply {
            setYesListener {
                stopRun()
            }
        }.show(parentFragmentManager, CANCEL_DIALOG_TAG)
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (::mapView.isInitialized) mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }
}
