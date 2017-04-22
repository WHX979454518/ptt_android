package com.xianzhitech.ptt.ui.dialog

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v7.app.AppCompatDialogFragment
import android.widget.TimePicker
import com.xianzhitech.ptt.util.parentAs
import org.threeten.bp.LocalTime

class TimePickerDialogFragment : AppCompatDialogFragment(), TimePickerDialog.OnTimeSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val time = arguments.getSerializable(ARG_INITIAL_TIME) as LocalTime
        return TimePickerDialog(context, this, time.hour, time.minute, true)
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        parentAs<OnTimeSetListener>().onTimeSet(this, LocalTime.of(hourOfDay, minute))
    }

    interface OnTimeSetListener {
        fun onTimeSet(dialogFragment: TimePickerDialogFragment, time: LocalTime)
    }

    companion object {
        const val ARG_INITIAL_TIME = "initial_time"

        fun createInstance(localTime: LocalTime) : TimePickerDialogFragment {
            return TimePickerDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putSerializable(ARG_INITIAL_TIME, localTime)
                }
            }
        }
    }
}