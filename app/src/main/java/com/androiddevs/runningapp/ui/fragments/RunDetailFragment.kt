package com.androiddevs.runningapp.ui.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.db.RunPoint
import com.androiddevs.runningapp.other.Constants.Companion.MAP_VIEW_BUNDLE_KEY
import com.androiddevs.runningapp.other.Constants.Companion.POLYLINE_COLOR
import com.androiddevs.runningapp.other.Constants.Companion.POLYLINE_WIDTH
import com.androiddevs.runningapp.ui.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RunDetailFragment : Fragment(R.layout.fragment_run_detail) {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var mapView: MapView
    private lateinit var tvRouteDetailStatus: TextView

    private var map: GoogleMap? = null
    private var routePoints: List<RunPoint> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapViewRunDetail)
        tvRouteDetailStatus = view.findViewById(R.id.tvRouteDetailStatus)

        val mapViewBundle = savedInstanceState?.getBundle(MAP_VIEW_BUNDLE_KEY)
        mapView.onCreate(mapViewBundle)

        mapView.getMapAsync { googleMap ->
            map = googleMap
            drawRoute()
        }

        val runId = requireArguments().getInt("runId")
        viewModel.getRunPoints(runId).observe(viewLifecycleOwner, Observer { points ->
            routePoints = points.orEmpty()
            drawRoute()
        })
    }

    private fun drawRoute() {
        val currentMap = map ?: return
        currentMap.clear()

        if (routePoints.isEmpty()) {
            tvRouteDetailStatus.visibility = View.VISIBLE
            tvRouteDetailStatus.text = getString(R.string.route_detail_no_points)
            return
        }

        tvRouteDetailStatus.visibility = View.GONE
        val latLngPoints = routePoints.map { LatLng(it.latitude, it.longitude) }
        currentMap.addPolyline(
            PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(latLngPoints)
        )

        if (latLngPoints.size == 1) {
            currentMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngPoints.first(), 16f))
            return
        }

        val bounds = LatLngBounds.Builder().apply {
            latLngPoints.forEach { include(it) }
        }.build()

        mapView.post {
            val width = mapView.width
            val height = mapView.height
            if (width > 0 && height > 0) {
                currentMap.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        width,
                        height,
                        (height * 0.08f).toInt()
                    )
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY) ?: Bundle().also {
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, it)
        }
        mapView.onSaveInstanceState(mapViewBundle)
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
