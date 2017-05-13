package com.xianzhitech.ptt.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Location
import com.xianzhitech.ptt.databinding.FragmentChatBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.e
import com.xianzhitech.ptt.ext.show
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.toRxObservable
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.image.ImageViewerFragment
import com.xianzhitech.ptt.ui.map.LocationViewFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChatFragment : BaseViewModelFragment<ChatViewModel, FragmentChatBinding>(), ChatViewModel.Navigator, BackPressable {
    private val chatAdapter = ChatAdapter()

    private var photoFile: Uri? = null

    private val inputMethodManager: InputMethodManager by lazy {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        photoFile = savedInstanceState?.getParcelable(STATE_PHOTO_PATH)

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)


    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        photoFile?.let { outState.putParcelable(STATE_PHOTO_PATH, it) }
    }

    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChatBinding {
        val binding = FragmentChatBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
        binding.recyclerView.adapter = chatAdapter
        binding.recyclerView.addItemDecoration(ChatDecoration)
        return binding
    }

    override fun onCreateViewModel(): ChatViewModel {
        return ChatViewModel(appComponent, context.applicationContext, chatAdapter.messages, arguments.getString(ARG_ROOM_ID), this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dataBinding.editText.requestFocus()
        dataBinding.editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.moreSelectionOpen.set(false)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (activity is FragmentDisplayActivity) {
            viewModel.title.toRxObservable()
                    .subscribe { activity.title = it.orNull() }
                    .bindToLifecycle()

            viewModel.moreSelectionOpen.toRxObservable()
                    .distinctUntilChanged()
                    .subscribe {
                        if (it.get()) {
                            dataBinding.editText.clearFocus()
                            inputMethodManager.hideSoftInputFromWindow(dataBinding.editText.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                        }
                        else {
                            inputMethodManager.showSoftInput(dataBinding.editText, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                    .bindToLifecycle()

            viewModel.moreSelectionOpen.toRxObservable()
                    .distinctUntilChanged()
                    .switchMap { open ->
                        if (open.get()) {
                            Observable.timer(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()).map { true }
                        } else {
                            Observable.just(open.get())
                        }
                    }
                    .subscribe { dataBinding.bottomSelection.show = it }
                    .bindToLifecycle()
        }
    }

    override fun navigateToVideoChatPage(roomId: String) {
        callbacks<Callbacks>()?.navigateToVideoChatPage(roomId)
    }

    override fun navigateToWalkieTalkie(roomId: String) {
        callbacks<Callbacks>()?.navigateToWalkieTalkiePage(roomId)
    }

    override fun navigateToImageViewer(url: String) {
        ImageViewerFragment.createInstance(url).let {
            it.isCancelable = true
            it.show(childFragmentManager, TAG_IMAGE_VIEWER)
        }
    }

    override fun displayNoPermissionToWalkie() {
        Toast.makeText(context, R.string.error_no_permission, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed(): Boolean {
        if (viewModel.moreSelectionOpen.get()) {
            viewModel.moreSelectionOpen.set(false)
            return true
        }

        return false
    }

    override fun openEmojiDrawer() {
        //TODO
    }

    override fun navigateToLatestMessageIfPossible() {
        val position = (dataBinding.recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        if (position <= 1) {
            dataBinding.recyclerView.smoothScrollToPosition(0)
        }
    }

    override fun navigateToWalkieTalkiePage() {
        callbacks<Callbacks>()?.navigateToWalkieTalkiePage()
    }

    override fun navigateToVideoChatPage() {
        callbacks<Callbacks>()?.navigateToVideoChatPage()
    }

    override fun navigateToPickAlbum() {
        val getIntent = Intent(Intent.ACTION_GET_CONTENT)
        getIntent.type = "image/*"
        startActivityForResult(getIntent, REQUEST_ALBUM)
    }

    override fun navigateToMap(location: Location) {
        activity.startActivityWithAnimation(
                FragmentDisplayActivity.createIntent(
                        LocationViewFragment::class.java,
                        Bundle(1).apply {
                            putSerializable(LocationViewFragment.ARG_LOCATION, location)
                        }
                )
        )
    }

    override fun navigateToCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"

        val file = File.createTempFile(imageFileName, ".jpg", context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        photoFile = Uri.fromFile(file)

        val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivityForResult(intent, REQUEST_CAMERA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when  {
            requestCode == REQUEST_ALBUM && resultCode == Activity.RESULT_OK && data != null -> {
                viewModel.onNewImage(data.data)
            }

            requestCode == REQUEST_CAMERA && resultCode == Activity.RESULT_OK && photoFile != null -> {
                viewModel.onNewImage(photoFile!!)
                photoFile = null
            }

            requestCode == REQUEST_CAMERA && photoFile != null -> {
                logger.e { "Error taking photo. Deleting temp file $photoFile" }
                File(photoFile!!.toString()).delete()
                photoFile = null
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    object ChatDecoration : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
            val vertical = view.resources.getDimensionPixelSize(R.dimen.unit_two)
            val horizontal = view.resources.getDimensionPixelSize(R.dimen.unit_one)
            val pos = parent.findContainingViewHolder(view)?.adapterPosition ?: return

            if (pos == parent.adapter.itemCount - 1) {
                outRect.top = vertical
            }

            outRect.left = horizontal
            outRect.right = horizontal
            outRect.bottom = vertical
        }
    }

    interface Callbacks {
        fun navigateToWalkieTalkiePage(roomId: String)
        fun navigateToWalkieTalkiePage()
        fun navigateToVideoChatPage()
        fun navigateToVideoChatPage(roomId: String)
    }

    companion object {
        const val ARG_ROOM_ID = "room_id"

        private const val REQUEST_ALBUM = 1
        private const val REQUEST_CAMERA = 2

        private const val TAG_IMAGE_VIEWER = "image_viewer"

        private const val STATE_PHOTO_PATH = "photo"

        fun createInstance(roomId: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_ROOM_ID, roomId)
                }
            }
        }
    }
}