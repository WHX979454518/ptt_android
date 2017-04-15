package com.xianzhitech.ptt.ui.modellist

import android.databinding.ObservableMap
import com.xianzhitech.ptt.data.NamedModel
import com.xianzhitech.ptt.viewmodel.ViewModel


data class ContactModelViewModel(val model: NamedModel,
                                 val checkedIds: ObservableMap<String, Boolean>,
                                 val checkable: Boolean) : ViewModel