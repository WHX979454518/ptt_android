package com.xianzhitech.ptt.ui.home

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable

open class ModelItemHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
    val nameView : TextView? = itemView.findViewById(R.id.modelItem_name) as TextView?
    val iconView : ImageView? = itemView.findViewById(R.id.modelItem_icon) as ImageView?

    var model : Model? = null
    set(value) {
        if (field != value) {
            field = value
            nameView?.text = value?.name
            iconView?.setImageDrawable(value?.createDrawable(itemView.context))
        }
    }
}