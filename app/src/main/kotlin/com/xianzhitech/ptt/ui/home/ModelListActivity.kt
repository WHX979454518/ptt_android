package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.modellist.ModelListFragment


class ModelListActivity : BaseToolbarActivity(), ModelListFragment.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.baseToolbar_root, ModelListFragment().apply {
                    arguments = Bundle(1).apply {
                        putParcelable(ModelListFragment.ARG_MODEL_PROVIDER, intent.getParcelableExtra<ModelProvider>(EXTRA_MODEL_PROVIDER))
                    }
                })
                .commit()

            supportFragmentManager.executePendingTransactions()
        }

        title = intent.getCharSequenceExtra(EXTRA_TITLE)
    }

    override fun onSelectionDone(selected: List<String>) {
        setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_SELECTED_MODEL_IDS, selected.toTypedArray()))
        finish()
    }

    override fun navigateToUser(user: User) {
    }

    override fun navigateToGroup(group: Group) {
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
