package com.infikey.ai

import android.content.Context
import android.util.Log

/**
 * Multi-model router.
 *  FAST tier  → default small on-device model (LFM2.5-1.2B) or active cloud
 *  DEEP tier  → user's chosen deep engine: an imported big GGUF, or a cloud endpoint
 */
class AiRouter(private val context: Context) {

    enum class Tier { FAST, DEEP }

    private val prefs = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)
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

    fun setDeepEngine(engine: AiEngine) { deepEngine = engine }

    fun setDeepCloud(endpointId: String) {
        endpoints.active(endpointId)?.let { setDeepEngine(CloudEngine(it)) }
    }

    fun activeCloud(): CloudEndpoint? {
        val id = prefs.getString("active_cloud_endpoint", "") ?: ""
        return endpoints.active(id) ?: endpoints.list().firstOrNull()
    }

    fun engineFor(tier: Tier): AiEngine? {
        val mode = prefs.getString("ai_mode", "auto") ?: "auto"
        if (mode == "cloud") return activeCloud()?.let { CloudEngine(it) }
        if (mode == "offline") return fastEngine
        return when (tier) {
            Tier.FAST -> fastEngine ?: activeCloud()?.let { CloudEngine(it) }
            Tier.DEEP -> deepEngine
                ?: if (prefs.getBoolean("deep_prefer_ondevice", false)) fastEngine
                else activeCloud()?.let { CloudEngine(it) }
        }
    }

    suspend fun chat(
        tier: Tier,
        messages: List<ChatMessage>,
        opts: GenOptions = GenOptions()
    ): Result<String> = runCatching {
        val engine = engineFor(tier)
            ?: throw IllegalStateException("no AI engine available — load a model or add a cloud API")
        engine.chat(messages, opts)
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
        return sb.toString()
    }

    fun unloadAll() {
        fastEngine?.unload()
        fastEngine = null
    }

    companion object { const val TAG = "AiRouter" }
}
