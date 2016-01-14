package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.view.ViewPager
import android.view.*
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getColorCompat
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ui.base.BaseFragment
import kotlin.collections.forEachIndexed

/**

 * 显示已经登陆用户的主界面

 * Created by fanchao on 17/12/15.
 */
class HomeFragment : BaseFragment<HomeFragment.Callbacks>() {

    private enum class Tab private constructor(@StringRes
                                               val labelRes: Int, @DrawableRes
                                               val drawableRes: Int, val fragmentClazz: Class<out Fragment>) {
        Conversation(R.string.tab_conversation, R.drawable.ic_chat_bubble, RoomListFragment::class.java),
        Contacts(R.string.tab_contacts, R.drawable.ic_people, ContactsFragment::class.java),
        Person(R.string.tab_me, R.drawable.ic_person, PersonFragment::class.java)
    }


    internal lateinit var viewPager: ViewPager
    internal lateinit var tabContainer: ViewGroup
    internal var selectedTintColor: Int = 0
    internal var normalTintColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_home, container, false)?.apply {
            viewPager = findView(R.id.home_viewPager)
            tabContainer = findView(R.id.home_tabContainer)
            normalTintColor = context.getColorCompat(R.color.secondary_text)
            selectedTintColor = context.getColorCompat(R.color.primary)

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

            if (savedInstanceState == null) {
                setTabItemSelected(0, true)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.home, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.home_logout) {
            (context.applicationContext as AppComponent).connectToBackgroundService()
                .flatMap { it.logout() }
                .first()
                .subscribe(GlobalSubscriber())
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        callbacks?.setTitle(getString(R.string.app_name))
    }

    private fun setTabItemSelected(position: Int, selected: Boolean) {
        val tabItem = tabContainer.getChildAt(position) as TextView
        tabItem.isSelected = selected
        DrawableCompat.setTint(tabItem.compoundDrawables[1], if (selected) selectedTintColor else normalTintColor)
    }

    interface Callbacks {
        fun setTitle(title: CharSequence)
    }
}
