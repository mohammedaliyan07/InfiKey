package com.infikey.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * On-device engine: runs any loaded GGUF through llama.cpp.
 * Works for the default LFM2.5-1.2B and for any imported GGUF.
 */
class OnDeviceEngine(
    private val modelPath: String,
    private val contextSize: Int = 2048,
    private val threads: Int = maxOf(2, Runtime.getRuntime().availableProcessors() - 2),
    private val gpuLayers: Int = 0
) : AiEngine {

    @Volatile
    private var handle = 0L

    /** The native context handles one generation at a time and must not be freed mid-run. */
    private val lock = Any()
    private var chatTemplate: String? = null

    override val id = "on-device:$modelPath"
    override val displayName = "On-device (${modelPath.substringAfterLast('/')})"
    override val isReady get() = handle != 0L

    suspend fun load(): Boolean = withContext(Dispatchers.Default) {
        if (!LlamaBridge.loadLibrary()) return@withContext false
        if (handle == 0L) {
            handle = LlamaBridge.nativeLoadModel(modelPath, contextSize, threads, gpuLayers)
            if (handle != 0L) {
                runCatching {
                    val info = LlamaBridge.nativeModelInfo(handle)
                    if (info != null) chatTemplate = JSONObject(info).optString("chat_template", "")
                }
            }
        }
        handle != 0L
    }

    fun unload() {
        cancel() // stop a running generation so the lock is released quickly
        synchronized(lock) {
            if (handle != 0L) {
                LlamaBridge.nativeFree(handle)
                handle = 0L
            }
        }
    }

    fun modelInfoJson(): String? =
        if (handle != 0L) LlamaBridge.nativeModelInfo(handle) else null

    override fun cancel() {
        if (handle != 0L) LlamaBridge.nativeCancel(handle)
    }

    override suspend fun chat(messages: List<ChatMessage>, opts: GenOptions): String {
        if (!load()) throw IllegalStateException("model not loaded: $modelPath")
        val prompt = ChatTemplates.apply(chatTemplate, modelPath.substringAfterLast('/'), messages)
        val sb = StringBuilder()
        val stop = opts.stop.firstOrNull()
        val listener = onTokenListener
        onTokenListener = null
        withContext(Dispatchers.Default) {
            val job = coroutineContext[Job]
            synchronized(lock) {
                check(handle != 0L) { "model was unloaded" }
                val ok = LlamaBridge.nativeGenerate(
                    handle, prompt, opts.maxTokens, opts.temperature, opts.topP, opts.topK,
                    stop, LlamaBridge.TokenCallback { tok ->
                        sb.append(tok)
                        listener?.invoke(tok)
                        job?.isActive != false // closing the panel stops generation
                    }
                )
                check(ok || sb.isNotEmpty()) { "generation failed — the text may be too long for this model" }
            }
        }
        return sb.toString().trim()
    }

    override suspend fun stream(
        messages: List<ChatMessage>,
        opts: GenOptions,
        onToken: (String) -> Unit
    ): String {
        onTokenListener = onToken
        return chat(messages, opts)
    }

    @Volatile
    private var onTokenListener: ((String) -> Unit)? = null
}

/** Chat template handling: auto-detected from GGUF metadata, with heuristics. */
object ChatTemplates {
    const val LFM2 = "lfm2"
    const val CHATML = "chatml"
    const val LLAMA3 = "llama3"
    const val PLAIN = "plain"

    fun detect(templateMeta: String?, modelName: String): String {
        val t = templateMeta ?: ""
        return when {
            t.contains("To: user") || t.contains("from:assistant") -> LFM2
            t.contains("im_start") -> CHATML
            t.contains("[INST]") -> LLAMA3
            modelName.contains("LFM", ignoreCase = true) -> LFM2
            modelName.contains("llama-3", ignoreCase = true) -> LLAMA3
            else -> CHATML
        }
    }

    fun apply(templateMeta: String?, modelName: String, messages: List<ChatMessage>): String {
        return when (detect(templateMeta, modelName)) {
            LFM2 -> lfm2(messages)
            LLAMA3 -> llama3(messages)
            PLAIN -> plain(messages)
            else -> chatml(messages)
        }
    }

    private fun lfm2(m: List<ChatMessage>): String {
        val sb = StringBuilder("<|startoftext|>")
        m.forEach { msg ->
            when (msg.role) {
                "system" -> sb.append("To: system\n").append(msg.content).append("\n")
                "user" -> sb.append("To: user\n").append(msg.content).append("<|from:assistant\n")
                "assistant" -> sb.append(msg.content).append("\n")
            }
        }
        return sb.toString()
    }

    private fun chatml(m: List<ChatMessage>): String {
        val sb = StringBuilder()
        m.forEach { msg -> sb.append("<|im_start|>").append(msg.role).append("\n").append(msg.content).append("<|im_end|>\n") }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun llama3(m: List<ChatMessage>): String {
        val sb = StringBuilder("<|begin_of_text|>")
        m.forEach { msg -> sb.append("<|start_header_id|>").append(msg.role).append("<|end_header_id|>\n\n").append(msg.content).append("<|eot_id|>") }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun plain(m: List<ChatMessage>): String {
        val sys = m.filter { it.role == "system" }.joinToString("\n") { it.content }
        val convo = m.filter { it.role != "system" }.joinToString("\n") {
            (if (it.role == "user") "User: " else "Assistant: ") + it.content
        }
        return (if (sys.isNotEmpty()) "$sys\n" else "") + convo + "\nAssistant:"
    }
}
