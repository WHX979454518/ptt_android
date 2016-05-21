package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.EditText
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.combineWith
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.fromTextChanged
import com.xianzhitech.ptt.ext.getString
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ext.toObservable
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import rx.Observable
import rx.schedulers.Schedulers
import java.text.Collator
import java.util.*
import java.util.concurrent.TimeUnit

class UserListActivity : BaseToolbarActivity() {
    private lateinit var searchView : EditText
    private lateinit var userListView : RecyclerView
    private lateinit var progressView : View

    private val adapter = Adapter()
    private val preselectedUserIds : Set<String> by lazy {
        hashSetOf(*intent.getStringArrayExtra(EXTRA_PRESELECTED_USER_IDS))
    }
    private val selectedUserIds = hashSetOf<String>()
    private val isSelectable : Boolean
        get() = intent.getBooleanExtra(EXTRA_SELECTABLE, false)

    private val isPreselectedUnselectable : Boolean
        get() = intent.getBooleanExtra(EXTRA_PRESELECTED_UNSELECTABLE, false)

    private val itemClickIntent : Intent? by lazy {
        intent.getParcelableExtra<Intent>(EXTRA_CLICK_INTENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_user_selection)

        searchView = findView(R.id.userSelection_search)
        userListView = findView(R.id.userSelection_list)
        progressView = findView(R.id.userSelection_progress)

        userListView.layoutManager = LinearLayoutManager(this)
        userListView.adapter = adapter

        if (isSelectable) {
            selectedUserIds.addAll(preselectedUserIds)
        }

        title = intent.getCharSequenceExtra(EXTRA_TITLE)

        //TODO: Implement search view
        searchView.setVisible(false)
    }

    override fun onStart() {
        super.onStart()

        intent.getParcelableExtra<UserProvider>(EXTRA_USER_PROVIDER).getUsers(this)
                .combineWith(searchView.fromTextChanged().debounce(500, TimeUnit.MILLISECONDS).startWith(searchView.getString()).distinctUntilChanged())
                .flatMap {
                    val (source, needle) = it
                    if (needle.isNullOrBlank()) {
                        // Returns the full result
                        source.toObservable().let {
                            if (Looper.getMainLooper() != Looper.myLooper()) {
                                // If we are not in main thread, post it
                                it.observeOnMainThread()
                            } else {
                                it
                            }
                        }
                    } else {
                        // Filter the search result
                        Observable.defer {
                            source.filter { it.name.contains(needle, ignoreCase = true) }.toObservable()
                        }.subscribeOn(Schedulers.computation()).observeOnMainThread()
                    }
                }
                .compose(bindToLifecycle())
                .subscribe(object : GlobalSubscriber<List<User>>(this) {
                    override fun onNext(t: List<User>) {
                        adapter.addUsers(t)
                        progressView.setVisible(false)
                    }
                })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isSelectable) {
            menuInflater.inflate(R.menu.user_selection, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (isSelectable) {
            val doneItem = menu.findItem(R.id.userSelectionMenu_done)
            doneItem.isEnabled = selectedUserIds.isNotEmpty()
            doneItem.title = R.string.finish_with_number.toFormattedString(this, selectedUserIds.size)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (isSelectable && item.itemId == R.id.userSelectionMenu_done) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_SELECTED_USER_IDS, selectedUserIds.toTypedArray()))
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onSelectedUserIdUpdated() {
        if (isSelectable) {
            supportInvalidateOptionsMenu()
        }
    }

    private class UserComparator : Comparator<User> {
        private val collator = Collator.getInstance(Locale.CHINESE)

        override fun compare(lhs: User, rhs: User): Int {
            if (lhs.id == rhs.id) {
                return 0
            }

            return collator.compare(lhs.name, rhs.name)
        }
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        private val userList = arrayListOf<User>()
        private val userComparator = UserComparator()

        fun addUsers(newUsers : Collection<User>) {
            var changed : Boolean = false
            newUsers.forEach { u ->
                if (userList.binarySearch(u, userComparator) < 0) {
                    userList.add(u)
                    changed = true
                }
            }

            if (changed) {
                newUsers.sortedWith(userComparator)
                notifyDataSetChanged()
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = userList[position]
            holder.setUser(user)
            if (holder.nameView is Checkable && isSelectable) {
                holder.nameView.isChecked = selectedUserIds.contains(user.id)
                holder.itemView.isEnabled = preselectedUserIds.contains(user.id).not() || isPreselectedUnselectable
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val holder = ViewHolder(layoutInflater.inflate(R.layout.view_user_item, parent, false))
            if (isSelectable) {
                holder.itemView.setOnClickListener {
                    holder.getUserId()?.let { userId ->
                        holder.nameView.toggle()
                        val success = if (holder.nameView.isChecked) {
                            selectedUserIds.add(userId)
                        } else {
                            selectedUserIds.remove(userId)
                        }

                        if (success) {
                            onSelectedUserIdUpdated()
                        }
                    }
                }
            }
            else  {
                holder.nameView.checkMarkDrawable = null

                if (itemClickIntent != null) {
                    holder.itemView.setOnClickListener {
                        startActivityWithAnimation(itemClickIntent!!)
                    }
                }
            }

            return holder
        }

        override fun getItemCount(): Int {
            return userList.size
        }
    }

    private class ViewHolder(view : View,
                             val nameView : CheckedTextView = view.findView(R.id.userItem_name)) : RecyclerView.ViewHolder(view) {
        private var user : User? = null
        private val iconSize = itemView.resources.getDimensionPixelSize(R.dimen.user_list_icon_size)

        fun getUserId() : String? {
            return user?.id
        }

        fun setUser(user : User) {
            if (this.user?.id != user.id) {
                this.user = user
                nameView.text = user.name

                val userDrawable = user.createAvatarDrawable(itemView.context)
                userDrawable.setBounds(0, 0, iconSize, iconSize)
                val compoundDrawables = nameView.compoundDrawables.apply {
                    this[0] = userDrawable
                }
                nameView.setCompoundDrawables(compoundDrawables[0], compoundDrawables[1], compoundDrawables[2], compoundDrawables[3])
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_USER_PROVIDER = "extra_user_provider"
        const val EXTRA_SELECTABLE = "extra_selectable"
        const val EXTRA_CLICK_INTENT = "extra_click_intent"
        const val EXTRA_PRESELECTED_USER_IDS = "extra_preselected_user_ids"
        const val EXTRA_PRESELECTED_UNSELECTABLE = "extra_preselected_unselectable"

        const val RESULT_EXTRA_SELECTED_USER_IDS = "extra_selected_user_ids"

        fun build(context: Context,
                  title: CharSequence,
                  userProvider: UserProvider,
                  selectable : Boolean,
                  itemClickIntent : Intent?,
                  preselectedUserIds : Collection<String>,
                  preselectedUnselectable : Boolean) : Intent {

            return Intent(context, UserListActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_USER_PROVIDER, userProvider)
                    .putExtra(EXTRA_SELECTABLE, selectable)
                    .putExtra(EXTRA_CLICK_INTENT, itemClickIntent)
                    .putExtra(EXTRA_PRESELECTED_USER_IDS, preselectedUserIds.toTypedArray())
                    .putExtra(EXTRA_PRESELECTED_UNSELECTABLE, preselectedUnselectable)
        }
    }
}