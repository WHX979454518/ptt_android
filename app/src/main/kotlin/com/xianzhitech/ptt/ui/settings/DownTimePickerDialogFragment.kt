package com.xianzhitech.ptt.ui.settings

import android.os.Bundle
import android.support.v7.app.AppCompatDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Spinner
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.model.DownTime
import com.xianzhitech.ptt.model.LocalTime
import com.xianzhitech.ptt.util.parentAs

class DownTimePickerDialogFragment : AppCompatDialogFragment() {

    private val initialDownTime : DownTime
    get() = arguments.getSerializable(ARG_INITIAL_DOWNTIME) as DownTime

    private lateinit var views : Views

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_time_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views = Views(view).apply {
            val endAdapter = Adapter(null)
            val startAdapter = Adapter(null)
            startTimeView.adapter = startAdapter
            endTimeView.adapter = endAdapter

            startTimeView.setSelection(startAdapter.indexOf(initialDownTime.startTime))
            endTimeView.setSelection(endAdapter.indexOf(initialDownTime.endTime))

            startTimeView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    endAdapter.startTime = startAdapter.getItem(position)
                }
            }


            okButton.setOnClickListener {
                val downTime = DownTime(
                        startTime = startAdapter.getItem(startTimeView.selectedItemPosition),
                        endTime = endAdapter.getItem(endTimeView.selectedItemPosition)
                )
                parentAs<OnTimeSetListener>().onTimeSet(downTime)
                dismiss()
            }

            cancelButton.setOnClickListener { dismiss() }
        }
    }

    interface OnTimeSetListener {
        fun onTimeSet(downTime : DownTime)
    }

    private class Views(rootView : View,
                        val startTimeView : Spinner = rootView.findView(R.id.timePicker_startTime),
                        val endTimeView : Spinner = rootView.findView(R.id.timePicker_endTime),
                        val okButton : View = rootView.findView(R.id.timePicker_ok),
                        val cancelButton : View = rootView.findView(R.id.timePicker_cancel))

    class Adapter(startTime : LocalTime? = null) : BaseAdapter() {
        var startTime : LocalTime? = startTime
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

        fun indexOf(localTime: LocalTime) : Int {
            return times.indexOf(localTime)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val time = getItem(position)
            val view : TextView
            if (convertView is TextView) {
                view = convertView
            }
            else {
                view = TextView(parent.context)
                val padding = view.resources.getDimensionPixelSize(R.dimen.unit_one)
                view.setPadding(padding, padding, padding, padding)
            }

            if (startTime == null || time > startTime!!) {
                view.text = time.toString()
            }
            else {
                view.text = parent.resources.getString(R.string.downtime_next_day_with_time, time.toString())
            }

            return view
        }

        override fun getItem(position: Int) = times[position]

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount() = times.size

        companion object {
            private val times = Array(48, {
                val hours = it / 2
                val minutes = 30 * (it - hours * 2)
                LocalTime(hourOfDay = hours, minute = minutes)
            })
        }
    }

    companion object {
        const val ARG_INITIAL_DOWNTIME = "downtime"
        const val ARG_TITLE = "arg_title"

        fun createInstance(downTime: DownTime, title: CharSequence) : DownTimePickerDialogFragment {
            return DownTimePickerDialogFragment().apply {
                arguments = Bundle(2).apply {
                    putSerializable(ARG_INITIAL_DOWNTIME, downTime)
                    putCharSequence(ARG_TITLE, title)
                }
            }
        }
    }
}