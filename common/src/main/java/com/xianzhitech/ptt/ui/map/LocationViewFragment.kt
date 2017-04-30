package com.xianzhitech.ptt.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.utils.CoordinateConverter
import com.xianzhitech.ptt.R
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

        mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLng(position))

        val drawable = DrawableCompat.wrap(ContextCompat.getDrawable(context, R.drawable.ic_location_on_black_30dp))
        DrawableCompat.setTint(drawable, ContextCompat.getColor(context, R.color.material_red300))

        val bitmap = Bitmap.createBitmap((drawable.intrinsicWidth * 1.5).toInt(), (drawable.intrinsicHeight * 1.5).toInt(), Bitmap.Config.ARGB_8888).apply {
            Canvas(this).let {
                drawable.setBounds(0, 0, width, height)
                drawable.draw(it)
            }
        }

        mapView.map.addOverlay(MarkerOptions()
                .position(position)
                .anchor(0.5f, 1f)
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
        )
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