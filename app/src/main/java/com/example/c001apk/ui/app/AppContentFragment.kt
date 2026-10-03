package com.example.c001apk.ui.app

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.example.c001apk.ui.base.BaseAppFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppContentFragment : BaseAppFragment<AppContentViewModel>() {

    private val type by lazy { arguments?.getString("type").orEmpty() }

    @Inject
    lateinit var viewModelAssistedFactory: AppContentViewModel.Factory
    override val viewModel by viewModels<AppContentViewModel> {
        AppContentViewModel.provideFactory(
            viewModelAssistedFactory,
            type.ifEmpty { "reply" },
            arguments?.getString("id").orEmpty(),
            arguments?.getString("packageName").orEmpty(),
        )
    }

    companion object {
        @JvmStatic
        fun newInstance(type: String, id: String, packageName: String) =
            AppContentFragment().apply {
                arguments = Bundle().apply {
                    putString("type", type)
                    putString("id", id)
                    putString("packageName", packageName)
                }
            }
    }

    override fun initObserve() {
        super.initObserve()

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

}