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
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.ContentViewEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.repo.getInRoomDescription
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.util.withUser
import rx.Observable
import java.util.*

/**

 * 显示已经登陆用户的主界面

 * Created by fanchao on 17/12/15.
 */
class HomeFragment : BaseFragment(), RoomListFragment.Callbacks {

    private enum class Tab constructor(@StringRes
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

    override fun requestCreateNewRoom() {
        callbacks<Callbacks>()?.requestCreateNewRoom()
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
        val signalService = appComponent.signalHandler
        val roomRepository = appComponent.roomRepository

        Answers.getInstance().logContentView(ContentViewEvent().apply {
            withUser(appComponent.signalHandler.peekCurrentUserId, appComponent.signalHandler.currentUserCache.value)
            putContentType("home")
        })

        Observable.combineLatest(
                signalService.loginStatus,
                signalService.roomStatus,
                signalService.roomState.distinctUntilChanged { it -> it.currentRoomId }
                        .switchMap {
                            val currUserId = appComponent.preference.userSessionToken?.userId ?: return@switchMap Observable.never<Pair<Room, RoomName>>()

                            Observable.combineLatest(
                                    roomRepository.getRoom(it.currentRoomId).observe(),
                                    roomRepository.getRoomName(it.currentRoomId, excludeUserIds = arrayOf(currUserId)).observe(),
                                    { first, second -> first to second }
                            )
                        },
                { loginStatus, roomStatus, roomInfo -> LoginRoomInfo(loginStatus, roomStatus, roomInfo?.first, roomInfo?.second) })
                .observeOnMainThread()
                .subscribeSimple {
                    views?.topBanner?.apply {
                        val (loginStatus, roomStatus, currRoom, roomName: RoomName?) = it
                        setCompoundDrawables(null, null, null, null)
                        when (loginStatus) {
                            LoginStatus.LOGIN_IN_PROGRESS -> {
                                setVisible(true)
                                setText(R.string.connecting_to_server)
                            }
                            LoginStatus.IDLE -> {
                                setVisible(true)
                                setText(R.string.error_unable_to_connect)
                            }
                            else -> {
                                if (EnumSet.of(RoomStatus.JOINED, RoomStatus.ACTIVE, RoomStatus.REQUESTING_MIC).contains(roomStatus) && currRoom != null) {
                                    setVisible(true)

                                    text = roomName.getInRoomDescription(context)

                                    setOnClickListener {
                                        (activity as BaseActivity).joinRoom(currRoom.id, false)
                                    }
                                } else {
                                    setVisible(false)
                                }
                            }
                        }
                    }
                }
                .bindToLifecycle()
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
        fun requestCreateNewRoom()
    }

    private data class LoginRoomInfo(val loginStatus: LoginStatus,
                                     val roomStatus: RoomStatus,
                                     val currRoom: Room?,
                                     val currRoomName: RoomName?)
}
