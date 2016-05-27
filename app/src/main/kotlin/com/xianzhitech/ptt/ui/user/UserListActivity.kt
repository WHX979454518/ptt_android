package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.EditText
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import rx.Observable
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class UserListActivity : BaseToolbarActivity() {
    private lateinit var searchView : EditText
    private lateinit var userListView : RecyclerView
    private lateinit var progressView : View

    private val preselectedUserIds : Set<String> by lazy {
        hashSetOf(*intent.getStringArrayExtra(EXTRA_PRESELECTED_USER_IDS))
    }
    private val selectedUserIds = hashSetOf<String>()
    private val isSelectable : Boolean
        get() = intent.getBooleanExtra(EXTRA_SELECTABLE, false)
    private val adapter = Adapter()

    private val isPreselectedUnselectable : Boolean
        get() = intent.getBooleanExtra(EXTRA_PRESELECTED_UNSELECTABLE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_user_list)

        searchView = findView(R.id.userSelection_search)
        userListView = findView(R.id.userSelection_list)
        progressView = findView(R.id.userSelection_progress)

        userListView.layoutManager = LinearLayoutManager(this)
        userListView.adapter = adapter

        title = intent.getCharSequenceExtra(EXTRA_TITLE)

        //TODO: Implement search view
        searchView.setVisible(false)
    }

    override fun onStart() {
        super.onStart()

        intent.getParcelableExtra<UserProvider>(EXTRA_USER_PROVIDER).getUsers(this)
                .combineWith(searchView.fromTextChanged().debounce(500, TimeUnit.MILLISECONDS).startWith(searchView.getString()).distinctUntilChanged())
                .switchMap {
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
                        adapter.setUsers(t)
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
            doneItem.isVisible = selectedUserIds.isNotEmpty()
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

    private inner class Adapter : UserListAdapter(R.layout.view_user_list_item) {
        override fun onBindViewHolder(holder: UserItemHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            if (isSelectable && holder.nameView is Checkable) {
                val userId = getUser(position).id
                val isPreselected = preselectedUserIds.contains(userId)
                holder.nameView.isChecked = selectedUserIds.contains(userId) || isPreselected
                holder.itemView.isEnabled = isPreselected.not() || isPreselectedUnselectable
            } else if (isSelectable.not() && holder.nameView is CheckedTextView) {
                holder.nameView.checkMarkDrawable = null as Drawable?
            }
        }

        override fun onItemClicked(userItemHolder: UserItemHolder) {
            if (isSelectable) {
                val userId = userItemHolder.userId!!
                if (preselectedUserIds.contains(userId).not()) {
                    val wasChecked = selectedUserIds.contains(userId)
                    if (wasChecked) {
                        selectedUserIds.remove(userId)
                    } else {
                        selectedUserIds.add(userId)
                    }

                    (userItemHolder.nameView as Checkable).isChecked = wasChecked.not()
                    onSelectedUserIdUpdated()
                }
            } else {
                super.onItemClicked(userItemHolder)
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_USER_PROVIDER = "extra_user_provider"
        const val EXTRA_SELECTABLE = "extra_selectable"
        const val EXTRA_PRESELECTED_USER_IDS = "extra_preselected_user_ids"
        const val EXTRA_PRESELECTED_UNSELECTABLE = "extra_preselected_unselectable"

        const val RESULT_EXTRA_SELECTED_USER_IDS = "extra_selected_user_ids"

        fun build(context: Context,
                  title: CharSequence,
                  userProvider: UserProvider,
                  selectable : Boolean,
                  preselectedUserIds : Collection<String>,
                  preselectedUnselectable : Boolean) : Intent {

            return Intent(context, UserListActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_USER_PROVIDER, userProvider)
                    .putExtra(EXTRA_SELECTABLE, selectable)
                    .putExtra(EXTRA_PRESELECTED_USER_IDS, preselectedUserIds.toTypedArray())
                    .putExtra(EXTRA_PRESELECTED_UNSELECTABLE, preselectedUnselectable)
        }
    }
}