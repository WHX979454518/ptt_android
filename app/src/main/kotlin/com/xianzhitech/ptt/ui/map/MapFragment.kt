package com.xianzhitech.ptt.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.baidu.location.LocationClient
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.dto.NearbyUser
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.util.receiveLocation
import com.xianzhitech.ptt.util.receiveMapStatus
import org.slf4j.LoggerFactory
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class MapFragment : BaseFragment() {
    private var mapView : MapView? = null
    private var myLocation : MyLocationData? = null
    private var userHasTouchedMap : Boolean = false
    private val userMarkers = arrayListOf<Marker>()
    private val personPin : BitmapDescriptor by lazy {
        BitmapDescriptorFactory.fromBitmap(ContextCompat.getDrawable(context, R.drawable.ic_person_pin_red).let { vd ->
            Bitmap.createBitmap(vd.intrinsicWidth, vd.intrinsicHeight, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0)
                val canvas = Canvas(this)
                vd.setBounds(0, 0, width, height)
                vd.draw(canvas)
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findView(R.id.map_view)
        mapView?.map?.let {
            it.isMyLocationEnabled = true
            it.setOnMapTouchListener { userHasTouchedMap = true }
        }

        view.findViewById(R.id.map_near_me).setOnClickListener { centerToCurrentLocation() }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mapView?.onDestroy()
        mapView = null
    }

    override fun onResume() {
        super.onResume()

        mapView?.let{
            it.onResume()
            val map = it.map
            map.receiveMapStatus()
                    .map { it.bound }
                    .distinctUntilChanged()
                    // 最小请求间隔为1秒
                    .debounce(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .switchMap { appComponent.signalHandler.searchNearbyUsers(it) }
                    .switchMap { users ->
                        val userMaps : Map<String, NearbyUser> = users.associateBy { it.userId }
                        appComponent.userRepository.getUsers(userMaps.keys).observe()
                                .map { userObjects ->
                                    userObjects.forEach { userMaps[it.id]!!.user = it }
                                    users
                                }
                    }
                    .subscribeSimple { users ->
                        logger.i { "Got ${users.size} users within current boundaries. Existing marker size ${userMarkers.size}" }
                        val markerSize = userMarkers.size
                        val size = Math.min(users.size, markerSize)
                        // 重复利用已经存在的Marker
                        for (i in 0..size - 1) {
                            val u = users[i]
                            userMarkers[i].position = u.latLng
                            userMarkers[i].title = u.user?.name ?: u.userId
                        }

                        if (size < markerSize) {
                            // 移除多余的Markers
                            for (i in markerSize - 1 downTo size) {
                                userMarkers[i].remove()
                                userMarkers.removeAt(i)
                            }
                        }
                        else if (size < users.size) {
                            // 新建Markers
                            for (i in size..users.size - 1) {
                                val u = users[i]
                                val newMarker = map.addOverlay(MarkerOptions().let {
                                    it.position(u.latLng)
                                    it.icon(personPin)
                                   // it.anchor(0.5f, 1f)
                                }) as Marker
                                logger.d { "Adding new marker $newMarker" }
                                userMarkers.add(newMarker)
                            }
                        }
                    }
                    .bindToLifecycle()
        }

        LocationClient(context).receiveLocation(false, 1000)
                .observeOnMainThread()
                .subscribeSimple {
                    val firstLocation = myLocation == null
                    myLocation = MyLocationData.Builder()
                            .latitude(it.lat)
                            .longitude(it.lng)
                            .accuracy(it.radius.toFloat())
                            .speed(it.speed.toFloat())
                            .direction(it.direction)
                            .build()
                    mapView?.map?.setMyLocationData(myLocation!!)

                    if (firstLocation && userHasTouchedMap.not()) {
                        centerToCurrentLocation()
                    }
                }
                .bindToLifecycle()
    }

    private fun centerToCurrentLocation() {
        if (myLocation != null) {
            mapView?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLng(LatLng(myLocation!!.latitude, myLocation!!.longitude)))
        }
    }

    override fun onPause() {
        mapView?.onPause()

        super.onPause()
    }

    companion object {
        private val logger = LoggerFactory.getLogger("MapFragment")
    }
}