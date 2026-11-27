package com.infikey.ai

import android.content.Context
import android.util.Log
import com.infikey.prefs.Prefs

/**
 * Multi-model router.
 *  FAST tier  → default small on-device model (LFM2.5-1.2B) or active cloud
 *  DEEP tier  → user's chosen deep engine: an imported big GGUF, or a cloud endpoint
 */
class AiRouter(private val context: Context) {

    enum class Tier { FAST, DEEP }

    private val prefs = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)
    private val cfg = Prefs(context)
    val models = ModelManager(context)

    private val endpoints = EndpointStore(
        { prefs.getString("cloud_endpoints", "[]") ?: "[]" },
        { prefs.edit().putString("cloud_endpoints", it).apply() }
    )

    @Volatile
    private var fastEngine: OnDeviceEngine? = null

    @Volatile
    private var deepEngine: AiEngine? = null

    var currentModelPath: String = prefs.getString("active_model_path", "") ?: ""
        private set

    /** Loads an on-device model (any GGUF). Unloads previous to keep memory sane. */
    suspend fun loadOnDevice(path: String, ctxSize: Int = 2048, gpuLayers: Int = 0): Boolean {
        val old = fastEngine
        val engine = OnDeviceEngine(path, ctxSize, gpuLayers = gpuLayers)
        val ok = engine.load()
        if (ok) {
            fastEngine = engine
            currentModelPath = path
            prefs.edit().putString("active_model_path", path).apply()
            old?.unload()
        } else {
            engine.unload()
        }
        return ok
    }

    fun isModelLoaded(): Boolean = fastEngine?.isReady == true

    /** Frees memory when the loaded model file is deleted from disk. */
    fun unloadIfCurrent(path: String) {
        if (currentModelPath == path) {
            fastEngine?.unload()
            fastEngine = null
            currentModelPath = ""
            prefs.edit().putString("active_model_path", "").apply()
        }
    }

    fun setDeepEngine(engine: AiEngine) { deepEngine = engine }

    fun setDeepCloud(endpointId: String) {
        endpoints.active(endpointId)?.let { setDeepEngine(CloudEngine(it)) }
    }

    fun activeCloud(): CloudEndpoint? {
        // any-site cloud API (#55): endpoints vanish when the toggle is off
        if (!cfg.feature("cloud_any")) return null
        val id = prefs.getString("active_cloud_endpoint", "") ?: ""
        return endpoints.active(id) ?: endpoints.list().firstOrNull()
    }

    fun engineFor(tier: Tier): AiEngine? {
        val mode = prefs.getString("ai_mode", "auto") ?: "auto"
        // fully offline mode (#59): airplane-proof — cloud engines never answer
        if (mode == "offline" || cfg.feature("offline_mode")) return fastEngine
        if (mode == "cloud") return activeCloud()?.let { CloudEngine(it) }
        return when (tier) {
            Tier.FAST -> fastEngine ?: activeCloud()?.let { CloudEngine(it) }
            // multi-model routing (#53): off → every tier runs the fast engine
            Tier.DEEP -> if (!cfg.feature("multi_model_router")) fastEngine
                else deepEngine ?: (if (prefs.getBoolean("deep_prefer_ondevice", false)) fastEngine
                    else activeCloud()?.let { CloudEngine(it) })
        }
    }

    suspend fun chat(
        tier: Tier,
        messages: List<ChatMessage>,
        opts: GenOptions = GenOptions()
    ): Result<String> = runCatching {
        val engine = engineFor(tier)
            ?: throw IllegalStateException("no AI engine available — load a model or add a cloud API")
        val out = engine.chat(messages, opts)
        logAi(engine, out.length)
        out
    }

    /** AI transparency log (#57): record every AI send, on-device or cloud. */
    private fun logAi(engine: AiEngine, chars: Int) {
        if (cfg.aiLogEnabled) cfg.appendAiLog(if (engine.id.startsWith("cloud")) "cloud" else "device", chars)
    }

    /**
     * Streaming chat used by the AI panel. Streams whatever the engine can
     * deliver; falls back to one-shot chat for engines without streaming.
     */
    suspend fun streamChat(
        messages: List<ChatMessage>,
        tone: String,
        fieldHint: String,
        onPartial: (String) -> Unit
    ): String {
        val engine = engineFor(Tier.FAST) ?: engineFor(Tier.DEEP)
            ?: throw IllegalStateException("no AI engine — load a model or add a cloud API")
        val sb = StringBuilder()
        engine.stream(messages, GenOptions(maxTokens = 600)) { token ->
            sb.append(token)
            onPartial(sb.toString())
        }
        logAi(engine, sb.length)
        return sb.toString()
    }

    fun unloadAll() {
        fastEngine?.unload()
        fastEngine = null
    }

    companion object { const val TAG = "AiRouter" }
}
