package com.xianzhitech.ptt.ui.modellist

import android.databinding.ObservableMap
import com.xianzhitech.ptt.model.NamedModel
import com.xianzhitech.ptt.ui.util.ViewModel


data class NamedModelViewModel(val model : NamedModel,
                               val checkedIds : ObservableMap<String, Boolean>,
                               val checkable : Boolean) : ViewModel