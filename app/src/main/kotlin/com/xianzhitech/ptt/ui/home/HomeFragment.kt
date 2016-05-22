package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.combineWith
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getColorCompat
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.repo.getInRoomDescription
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.loginStatus
import com.xianzhitech.ptt.service.roomStatus
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import rx.Observable
import java.util.*

/**

 * 显示已经登陆用户的主界面

 * Created by fanchao on 17/12/15.
 */
class HomeFragment : BaseFragment(), RoomListFragment.Callbacks {

    private enum class Tab private constructor(@StringRes
                                               val labelRes: Int, @DrawableRes
                                               val drawableRes: Int, val fragmentClazz: Class<out Fragment>) {
        Conversation(R.string.tab_conversation, R.drawable.ic_chat_bubble, RoomListFragment::class.java),
        Contacts(R.string.tab_contacts, R.drawable.ic_people, ContactsFragment::class.java),
        Person(R.string.tab_me, R.drawable.ic_person, ProfileFragment::class.java)
    }

    private class Views(rootView: View,
                        val viewPager: ViewPager = rootView.findView(R.id.home_viewPager),
                        val tabContainer: ViewGroup = rootView.findView(R.id.home_tabContainer),
                        val topBanner: TextView = rootView.findView(R.id.home_topBanner))

    private var selectedTintColor: Int = 0
    private var normalTintColor: Int = 0
    private var views: Views? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_home, container, false)?.apply {
            normalTintColor = context.getColorCompat(R.color.secondary_text)
            selectedTintColor = context.getColorCompat(R.color.primary)

            views = Views(this).apply {
                val tabs = Tab.values()
                tabs.forEachIndexed { i, tab ->
                    val tabView = inflater.inflate(R.layout.view_tab, tabContainer, false) as TextView
                    tabView.setCompoundDrawablesWithIntrinsicBounds(null, context.getTintedDrawable(tab.drawableRes, normalTintColor), null, null)
                    tabView.setText(tab.labelRes)
                    tabView.setOnClickListener { v -> viewPager.setCurrentItem(i, true) }
                    tabContainer.addView(tabView)
                }

                viewPager.adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
                    override fun getItem(position: Int): Fragment {
                        return Fragment.instantiate(context, tabs[position].fragmentClazz.name)
                    }

                    override fun getCount(): Int {
                        return tabs.size
                    }
                }

                viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageSelected(position: Int) {
                        var i = 0
                        val childCount = tabContainer.childCount
                        while (i < childCount) {
                            setTabItemSelected(i, i == position)
                            i++
                        }
                    }
                })
            }

            if (savedInstanceState == null) {
                setTabItemSelected(0, true)
            }
        }
    }


    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun navigateToContactList() {
        views?.viewPager?.setCurrentItem(Tab.Contacts.ordinal, true)
    }

    override fun onStart() {
        super.onStart()

        callbacks<Callbacks>()?.setTitle(getString(R.string.app_name))

        val appComponent = context.applicationContext as AppComponent
        val signalService = appComponent.signalService
        val roomRepository = appComponent.roomRepository
        Observable.combineLatest(
                signalService.loginStatus,
                signalService.roomStatus,
                signalService.roomState.distinctUntilChanged { it.currentRoomId }
                        .flatMap {
                            roomRepository.getRoom(it.currentRoomId).observe()
                                    .combineWith(roomRepository.getRoomName(it.currentRoomId, excludeUserIds = arrayOf(signalService.peekLoginState().currentUserID!!)).observe())
                        },
                { loginStatus, roomStatus, roomInfo -> LoginRoomInfo(loginStatus, roomStatus, roomInfo?.first, roomInfo?.second) })
                .compose(bindToLifecycle())
                .observeOnMainThread()
                .subscribeSimple {
                    views?.topBanner?.apply {
                        val (loginStatus, roomStatus, currRoom, roomName : RoomName?) = it
                        setCompoundDrawables(null, null, null, null)
                        when (loginStatus) {
                            LoginStatus.LOGIN_IN_PROGRESS -> {
                                setVisible(true)
                                setText(R.string.connecting_to_server)
                            }
                            LoginStatus.OFFLINE -> {
                                setVisible(true)
                                setText(R.string.error_unable_to_connect)
                            }
                            else -> {
                                if (EnumSet.of(RoomStatus.JOINED, RoomStatus.ACTIVE, RoomStatus.REQUESTING_MIC).contains(roomStatus) && currRoom != null) {
                                    setVisible(true)

                                    text = roomName.getInRoomDescription(context)

                                    setOnClickListener {
                                        (activity as BaseActivity).joinRoom(currRoom.id)
                                    }
                                }
                                else {
                                    setVisible(false)
                                }
                            }
                        }
                    }
                }
    }

    private fun setTabItemSelected(position: Int, selected: Boolean) {
        views?.apply {
            val tabItem = tabContainer.getChildAt(position) as TextView
            tabItem.isSelected = selected
            DrawableCompat.setTint(tabItem.compoundDrawables[1], if (selected) selectedTintColor else normalTintColor)
        }
    }

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }

    private data class LoginRoomInfo(val loginStatus: LoginStatus,
                                     val roomStatus: RoomStatus,
                                     val currRoom : Room?,
                                     val currRoomName : RoomName?)
}
