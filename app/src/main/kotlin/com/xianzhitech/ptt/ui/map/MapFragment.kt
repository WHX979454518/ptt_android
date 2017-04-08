package com.xianzhitech.ptt.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.baidu.location.LocationClient
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.dto.NearbyUser
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.user.UserPopupDialog
import com.xianzhitech.ptt.ui.widget.drawable.TextDrawable
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import com.xianzhitech.ptt.util.receiveLocation
import com.xianzhitech.ptt.util.receiveMapStatus
import org.slf4j.LoggerFactory
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class MapFragment : BaseFragment() {
    private var mapView : MapView? = null
    private var myLocation : MyLocationData? = null
    private var userHasTouchedMap : Boolean = false
    private val userMarkers = hashMapOf<String, MarkerData>()
    private var selectedUserId : String? = null
    private val personPinView : ImageView by lazy {
        ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = context.getTintedDrawable(R.drawable.ic_nearby_people, R.color.accent.toColorValue(context))
        }
    }

    private val tintedPersonPinView : ImageView by lazy {
        ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = context.getTintedDrawable(R.drawable.ic_nearby_people, R.color.red.toColorValue(context))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedUserId = savedInstanceState?.getString(STATE_SELECTED_USER_ID)
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
            it.setOnMarkerClickListener {
                val userId = it.extraInfo.getString(EXTRA_INFO_USER_ID)
                if (selectedUserId != userId) {
                    val oldSelectedUserId = selectedUserId
                    selectedUserId = userId

                    // 选择的图标发生变化，需要重新生成旧的图片和新的图片
                    if (oldSelectedUserId != null) {
                        val marker = userMarkers[oldSelectedUserId]
                        if (marker != null) {
                            marker.user.toMarker(marker.marker)
                        }
                    }

                    if (userId != null) {
                        val marker = userMarkers[userId]
                        if (marker != null) {
                            marker.user.toMarker(marker.marker)
                        }
                    }
                }

                if (userId != null) {
                    UserPopupDialog.create(userId).show(childFragmentManager, TAG_USER_DETAILS_DIALOG)
                }

                true
            }
        }

        view.findViewById(R.id.map_near_me).setOnClickListener { centerToCurrentLocation() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        mapView?.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_USER_ID, selectedUserId)
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
                    .map { users ->
                        val currentUserId = appComponent.signalHandler.peekCurrentUserId
                        users.filterNot { it.userId == currentUserId }
                    }
                    .switchMap { users ->
                        val userMaps : Map<String, NearbyUser> = users.associateBy { it.userId }
                        appComponent.userRepository.getUsers(userMaps.keys).observe()
                                .map { userObjects ->
                                    userObjects.forEach { userMaps[it.id]!!.user = it }
                                    userMaps
                                }
                    }
                    .subscribeSimple { users ->
                        logger.i { "Got ${users.size} users within current boundaries. Existing marker size ${userMarkers.size}" }

                        val removingMarkers = arrayListOf<MarkerData>()
                        val existingMarkersIter = userMarkers.iterator()
                        while (existingMarkersIter.hasNext()) {
                            val entry = existingMarkersIter.next()
                            if (users.containsKey(entry.key).not()) {
                                existingMarkersIter.remove()
                                removingMarkers.add(entry.value)
                            }
                        }

                        users.values.forEach { user ->
                            var existingMarker = userMarkers[user.userId]

                            // 如果当前用户没有缓存图像，那么看看可不可以从待删除的用户中拿一个？
                            if (existingMarker == null && removingMarkers.isNotEmpty()) {
                                existingMarker = removingMarkers.removeAt(removingMarkers.lastIndex)
                                userMarkers[user.userId] = existingMarker
                            }

                            // 如果都没有待删除的用户了，只好新建一个
                            if (existingMarker == null) {
                                userMarkers[user.userId] = MarkerData(user, map.addOverlay(user.toMarkerOption()) as Marker)
                            }
                            else {
                                // 有可以用的缓存...
                                user.toMarker(existingMarker.marker)
                            }
                        }

                        removingMarkers.forEach { it.marker.remove() }
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

    private fun NearbyUser.toMarker(marker: Marker) : Marker {
        marker.position = latLng
        val drawable = user?.createDrawable() ?: TextDrawable("?", Color.TRANSPARENT)
        val view = if (userId == selectedUserId) tintedPersonPinView else personPinView
        view.setImageDrawable(drawable)
        marker.icon = BitmapDescriptorFactory.fromView(view)
        marker.setAnchor(0.5f, 1f)
        marker.title = user?.name ?: userId
        marker.extraInfo = Bundle(1).apply {
            putString(EXTRA_INFO_USER_ID, userId)
        }

        if (selectedUserId == userId) {
            marker.zIndex = 1
        }
        else {
            marker.zIndex = 0
        }
        return marker
    }

    private fun NearbyUser.toMarkerOption() : MarkerOptions {
        return MarkerOptions().apply {
            position(latLng)
            val drawable = user?.createDrawable() ?: TextDrawable("?", Color.TRANSPARENT)
            val view = if (userId == selectedUserId) tintedPersonPinView else personPinView
            view.setImageDrawable(drawable)
            icon(BitmapDescriptorFactory.fromView(view))
            anchor(0.5f, 1f)
            title(user?.name ?: userId)
            extraInfo(Bundle(1).apply {
                putString(EXTRA_INFO_USER_ID, userId)
            })

            if (selectedUserId == userId) {
                zIndex(1)
            }
            else {
                zIndex(0)
            }
        }
    }

    private data class MarkerData(val user : NearbyUser,
                                  val marker: Marker)

    companion object {
        private val logger = LoggerFactory.getLogger("MapFragment")

        private const val TAG_USER_DETAILS_DIALOG = "tag_user_details"

        private const val EXTRA_INFO_USER_ID = "user_id"
        private const val STATE_SELECTED_USER_ID = "selected_user_id"
    }
}