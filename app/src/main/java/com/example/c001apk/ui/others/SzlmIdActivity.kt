package com.example.c001apk.ui.others

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivitySzlmIdBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.LocalDuidClient
import com.example.c001apk.util.SzlmIdPrompt
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Native issuance page. The ViewModel retains an in-flight request across configuration changes. */
class SzlmIdActivity : BaseActivity<ActivitySzlmIdBinding>() {
    private val model by lazy { ViewModelProvider(this)[SzlmIdViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.toolBar.title = getString(R.string.szlm_id_title)
        binding.toolBar.setNavigationIcon(R.drawable.ic_back)
        binding.toolBar.setNavigationOnClickListener { finish() }
        binding.retry.setOnClickListener { model.fetch(applicationContext) }
        binding.manual.setOnClickListener { SzlmIdPrompt.showManualInput(this) { finish() } }
        binding.cancel.setOnClickListener { finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.status.setText(state.error ?: R.string.szlm_id_fetch_loading)
                    binding.retry.isVisible = state.error != null
                    binding.manual.isVisible = state.error != null
                    state.id?.let {
                        Toast.makeText(this@SzlmIdActivity, R.string.szlm_id_fetch_success, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
        if (model.state.value == SzlmIdViewModel.State()) model.fetch(applicationContext)
    }
}

class SzlmIdViewModel : ViewModel() {
    data class State(val loading: Boolean = false, val id: String? = null, val error: Int? = null)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    fun fetch(context: Context) {
        if (state.value.loading || state.value.id != null) return
        mutableState.value = State(loading = true)
        viewModelScope.launch {
            try {
                val id = LocalDuidClient.fetch(context.applicationContext)
                SzlmIdPrompt.save(id)
                mutableState.value = State(id = id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                mutableState.value = State(error = R.string.szlm_id_fetch_ssaid_error)
            } catch (_: SSLException) {
                mutableState.value = State(error = R.string.szlm_id_fetch_ssl_error)
            } catch (_: IOException) {
                mutableState.value = State(error = R.string.szlm_id_fetch_error)
            } catch (_: Exception) {
                mutableState.value = State(error = R.string.szlm_id_fetch_error)
            }
        }
    }
}
