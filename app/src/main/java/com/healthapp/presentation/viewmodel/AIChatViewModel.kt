package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class ChatBubble(val fromUser: Boolean, val text: String)
data class AIChatUiState(val messages: List<ChatBubble> = emptyList(), val sending: Boolean = false, val error: UiText? = null)

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val accountManager: AccountSession,
    private val repository: AIRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AIChatUiState())
    val state = mutable.asStateFlow()

    fun send(question: String) {
        val clean = question.trim()
        if (clean.isEmpty() || clean.length > 2000 || mutable.value.sending) return
        mutable.value = mutable.value.copy(messages = mutable.value.messages + ChatBubble(true, clean), sending = true, error = null)
        viewModelScope.launch {
            runCatching { repository.chatWithAssistant(accountManager.getCurrentAccountId(), clean).getOrThrow() }
                .onSuccess { answer -> mutable.value = mutable.value.copy(messages = mutable.value.messages + ChatBubble(false, answer), sending = false) }
                .onFailure { error ->
                    val message = when {
                        error is IOException -> UiText.Res(R.string.chat_err_offline)
                        // Backend errors arrive already localized (quota, key, …) from the data layer.
                        error is IllegalStateException && !error.message.isNullOrBlank() -> UiText.Raw(error.message!!)
                        else -> UiText.Res(R.string.chat_err_generic)
                    }
                    mutable.value = mutable.value.copy(sending = false, error = message)
                }
        }
    }
}
