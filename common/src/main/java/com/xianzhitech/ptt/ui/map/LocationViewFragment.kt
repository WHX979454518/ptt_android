package com.xianzhitech.ptt.ui.map

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.utils.CoordinateConverter
import com.xianzhitech.ptt.ui.base.BaseFragment


class LocationViewFragment : BaseFragment() {
    private lateinit var mapView : MapView

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mapView = MapView(context)
        mapView.onCreate(context, savedInstanceState)
        mapView.showScaleControl(true)

        val loc : Location = arguments.getParcelable(ARG_LOCATION)

        val position = CoordinateConverter().let {
            it.from(CoordinateConverter.CoordType.GPS)
            it.coord(LatLng(loc.latitude, loc.longitude))
            it.convert()
        }

        mapView.map.setMyLocationData(MyLocationData.Builder().latitude(position.latitude).longitude(position.longitude).accuracy(loc.accuracy).build())
        return mapView
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onResume() {
        super.onResume()

        mapView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        mapView.onSaveInstanceState(outState)
    }

    companion object {
        const val ARG_LOCATION = "location"

        fun create(location : Location) : LocationViewFragment {
            return LocationViewFragment().apply {
                arguments = Bundle(1).apply {
                    putParcelable(ARG_LOCATION, location)
                }
            }
        }
    }
}