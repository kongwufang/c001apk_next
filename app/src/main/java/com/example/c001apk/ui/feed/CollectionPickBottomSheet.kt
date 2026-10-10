package com.example.c001apk.ui.feed

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.databinding.DialogCollectionEditBinding
import com.example.c001apk.databinding.FragmentCollectionPickBinding
import com.example.c001apk.databinding.ItemCollectionPickBinding
import com.example.c001apk.logic.model.CollectionAction
import com.example.c001apk.logic.model.CollectionData
import com.example.c001apk.util.Event
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.makeToast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

/**
 * 收藏到收藏夹：点条目收藏/取消收藏，长按改收藏夹（标题/简介/公开私密/封面），右上角新建。
 */
@AndroidEntryPoint
class CollectionPickBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentCollectionPickBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<CollectionPickViewModel>()

    private lateinit var editBinding: DialogCollectionEditBinding

    private var feedId: String = ""
    private var editCoverUri: Uri? = null

    /** 收藏结果通知外面（详情页底栏改收藏数 + 点亮/熄灭星标），参数是服务端回的最新状态 */
    var onChanged: ((CollectionAction) -> Unit)? = null

    private val pickCover = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        editCoverUri = uri
        editBinding.cover.setImageURI(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionPickBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        feedId = arguments?.getString("feedId").orEmpty()

        val adapter = PickAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.newCollection.setOnClickListener { showEditDialog(null) }

        viewModel.list.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            binding.emptyTip.isVisible = it.isEmpty()
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.indicator.isIndeterminate = it
            binding.indicator.isVisible = it
        }
        viewModel.toastText.observe(viewLifecycleOwner) { event: Event<String>? ->
            event?.getContentIfNotHandledOrReturnNull()?.let {
                requireContext().makeToast(it)
            }
        }
        // 只有服务端回了结果才动 UI：操作失败、纯改收藏夹信息都不该动底栏
        viewModel.actionState.observe(viewLifecycleOwner) { action ->
            // 勾就地改这一项，不再重拉列表。改的是同一个对象、内容比较看不出来，必须显式重绑这一行
            val index = adapter.currentList.indexOfFirst { it.id == action.collectionId }
            if (index >= 0) {
                adapter.currentList[index].isBeCollected = action.collect
                adapter.notifyItemChanged(index)
            }
            onChanged?.invoke(action)
        }

        viewModel.load(feedId)
    }

    private fun showEditDialog(item: CollectionData?) {
        editCoverUri = null
        editBinding = DialogCollectionEditBinding.inflate(layoutInflater)
        editBinding.cover.setOnClickListener {
            pickCover.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        if (item != null) {
            editBinding.title.setText(item.title.orEmpty())
            editBinding.description.setText(item.description.orEmpty())
            editBinding.isOpen.isChecked = item.is_open == 1
            if (!item.cover_pic.isNullOrEmpty())
                ImageUtil.showIMG(editBinding.cover, item.cover_pic)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (item == null) R.string.collection_create else R.string.collection_edit
            )
            .setView(editBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = editBinding.title.text?.toString()?.trim().orEmpty()
                if (title.isEmpty()) {
                    requireContext().makeToast("请填写收藏夹名称")
                    return@setPositiveButton
                }
                val description = editBinding.description.text?.toString()?.trim().orEmpty()
                val isOpen = if (editBinding.isOpen.isChecked) 1 else 0
                if (item == null)
                    viewModel.create(
                        feedId, title, description, isOpen, editCoverUri,
                        requireContext().contentResolver
                    )
                else
                    viewModel.update(
                        item.id.orEmpty(), title, description, isOpen, editCoverUri,
                        item.cover_pic, requireContext().contentResolver, feedId
                    )
            }
            .show()
    }

    /** inner class 里不能再声明嵌套类，ViewHolder 提到外层 */
    private class PickViewHolder(val binding: ItemCollectionPickBinding) :
        RecyclerView.ViewHolder(binding.root)

    private inner class PickAdapter :
        ListAdapter<CollectionData, PickViewHolder>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickViewHolder =
            PickViewHolder(
                ItemCollectionPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        override fun onBindViewHolder(holder: PickViewHolder, position: Int) {
            val item = getItem(position)
            with(holder.binding) {
                name.text = item.title
                desc.text = buildString {
                    append(item.item_num ?: 0)
                    append(" 条内容")
                    if (!item.is_open_title.isNullOrEmpty()) {
                        append(" · ")
                        append(item.is_open_title)
                    }
                }
                check.isVisible = item.isBeCollected == 1
                if (!item.cover_pic.isNullOrEmpty())
                    ImageUtil.showIMG(cover, item.cover_pic)
                root.setOnClickListener { viewModel.toggle(feedId, item) }
                root.setOnLongClickListener {
                    showEditDialog(item)
                    true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CollectionData>() {
            override fun areItemsTheSame(oldItem: CollectionData, newItem: CollectionData) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CollectionData, newItem: CollectionData) =
                oldItem == newItem
        }
    }
}
