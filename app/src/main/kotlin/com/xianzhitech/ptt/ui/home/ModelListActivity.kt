package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import android.widget.CheckedTextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import rx.Observable
import java.util.*


class ModelListActivity : BaseToolbarActivity(), ModelListFragmentImpl.Callbacks {
    private lateinit var progressView : View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_model_list)

        progressView = findView(R.id.modelList_progress)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, ModelListFragmentImpl().apply {
                    arguments = Bundle(1).apply {
                        putParcelable(ModelListFragmentImpl.ARG_MODEL_PROVIDER, intent.getParcelableExtra<ModelProvider>(EXTRA_MODEL_PROVIDER))
                    }
                })
                .commit()

            supportFragmentManager.executePendingTransactions()
        }

        title = intent.getCharSequenceExtra(EXTRA_TITLE)
    }

    override fun onDataLoadFinished() {
        progressView.setVisible(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.model_list, menu)
        (menu.findItem(R.id.modelList_search).actionView as SearchView).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                (supportFragmentManager.findFragmentById(R.id.content) as? ModelListFragmentImpl)?.search(newText)
                return true
            }
        })

        return ret
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.modelList_done) {
            val fragment = supportFragmentManager.findFragmentById(R.id.content) as? ModelListFragmentImpl
            if (fragment != null) {
                setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_SELECTED_MODEL_IDS, fragment.selectedIds.toTypedArray()))
                finish()
            }

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.modelList_done).let { item ->
            val fragment = supportFragmentManager.findFragmentById(R.id.content) as? ModelListFragmentImpl
            if (fragment == null || fragment.selectedIds.isEmpty()) {
                item.isEnabled = false
                item.title = R.string.finish.toFormattedString(this)
            } else {
                item.isEnabled = true
                item.title = R.string.finish_with_number.toFormattedString(this, fragment.selectedIds.size)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onSelectedItemsChanged() {
        supportInvalidateOptionsMenu()
    }

    companion object {
        const val EXTRA_MODEL_PROVIDER = "extra_mp"
        const val EXTRA_TITLE = "extra_title"

        const val RESULT_EXTRA_SELECTED_MODEL_IDS = "result_esmi"

        fun build(context: Context, title : CharSequence, provider: ModelProvider) : Intent {
            return Intent(context, ModelListActivity::class.java).putExtra(EXTRA_MODEL_PROVIDER, provider).putExtra(EXTRA_TITLE, title)
        }
    }
}

class ModelListFragmentImpl : ModelListFragment() {
    private val provider: ModelProvider by lazy { arguments.getParcelable<ModelProvider>(ARG_MODEL_PROVIDER) }

    val selectedIds = hashSetOf<String>()

    override val allModels: Observable<List<NamedModel>>
        get() = provider.getModels(context)

    override fun onCreateModelViewHolder(container: ViewGroup): RecyclerView.ViewHolder {
        return ModelItemHolder(LayoutInflater.from(container.context).inflate(R.layout.view_checkable_model_list_item, container, false)).apply {
            if (provider.selectable.not()) {
                (nameView as CheckedTextView).checkMarkDrawable = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            selectedIds.addAll(savedInstanceState.getStringArrayList(STATE_SELECTED_IDS))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArrayList(STATE_SELECTED_IDS, ArrayList(selectedIds))
    }

    override fun onItemClicked(viewHolder: RecyclerView.ViewHolder, model: NamedModel) {
        if (provider.selectable) {
            viewHolder as ModelItemHolder
            viewHolder.nameView as CheckedTextView
            if (provider.preselectedModelIds.contains(model.id).not() || provider.preselectedUnselectable) {
                viewHolder.nameView.toggle()
                if (viewHolder.nameView.isChecked) {
                    selectedIds.add(model.id)
                } else {
                    selectedIds.remove(model.id)
                }
                callbacks<Callbacks>()?.onSelectedItemsChanged()
            }
        } else {
            super.onItemClicked(viewHolder, model)
        }
    }

    override fun onBindModelViewHolder(viewHolder: RecyclerView.ViewHolder, model: NamedModel) {
        viewHolder as ModelItemHolder
        viewHolder.model = model
        if (provider.selectable) {
            viewHolder.nameView as CheckedTextView
            val isPreselected = provider.preselectedModelIds.contains(model.id)
            viewHolder.nameView.isChecked = selectedIds.contains(model.id) || isPreselected
            viewHolder.itemView.isEnabled = isPreselected.not() || provider.preselectedUnselectable
        }
    }

    override fun onDataLoadFinished() {
        super.onDataLoadFinished()
        callbacks<Callbacks>()?.onDataLoadFinished()
    }

    interface Callbacks {
        fun onSelectedItemsChanged()
        fun onDataLoadFinished()
    }

    companion object {
        const val ARG_MODEL_PROVIDER = "arg_mp"

        private const val STATE_SELECTED_IDS = "state_selected_ids"
    }
}