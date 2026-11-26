package com.infikey.ai

data class ChatMessage(val role: String, val content: String) {
    companion object {
        fun system(c: String) = ChatMessage("system", c)
        fun user(c: String) = ChatMessage("user", c)
        fun assistant(c: String) = ChatMessage("assistant", c)
    }
}

data class GenOptions(
    val maxTokens: Int = 300,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val stop: List<String> = emptyList()
)

interface AiEngine {
    val id: String
    val displayName: String
    val isReady: Boolean

    suspend fun chat(messages: List<ChatMessage>, opts: GenOptions = GenOptions()): String

    suspend fun stream(
        messages: List<ChatMessage>,
        opts: GenOptions = GenOptions(),
        onToken: (String) -> Unit
    ): String {
        val full = chat(messages, opts)
        onToken(full)
        return full
    }

    fun cancel() {}
}
