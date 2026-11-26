package com.infikey.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One cloud API endpoint. Works with ANY site that speaks OpenAI-compatible chat. */
data class CloudEndpoint(
    val id: String,
    val name: String,
    val baseUrl: String,      // any site, any link: https://api.groq.com/openai/v1, http://192.168.1.5:11434/v1 ...
    val apiKey: String,
    val model: String,
    val headers: Map<String, String> = emptyMap(), // extra headers for exotic providers
    val maxTokens: Int = 600
) {
    /** Normalizes any base URL into a chat-completions URL. */
    fun completionUrl(): String {
        val b = baseUrl.trim().trimEnd('/')
        return when {
            b.endsWith("/chat/completions") -> b
            b.endsWith("/v1") -> "$b/chat/completions"
            b.endsWith("/v2") -> "$b/chat/completions"
            b.endsWith("/openai") -> "$b/v1/chat/completions"
            else -> "$b/v1/chat/completions"
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("baseUrl", baseUrl)
        .put("apiKey", com.infikey.ime.Crypt.encrypt(apiKey))
        .put("model", model)
        .put("headers", JSONObject(headers)).put("maxTokens", maxTokens)

    companion object {
        fun fromJson(o: JSONObject) = CloudEndpoint(
            id = o.optString("id", ""),
            name = o.optString("name", "endpoint"),
            baseUrl = o.optString("baseUrl", ""),
            apiKey = com.infikey.ime.Crypt.decrypt(o.optString("apiKey", "")),
            model = o.optString("model", ""),
            headers = (o.optJSONObject("headers") ?: JSONObject()).let { h ->
                h.keys().asSequence().associateWith { k -> h.optString(k) }
            },
            maxTokens = o.optInt("maxTokens", 600)
        )
    }
}

/** Engine that talks to any OpenAI-compatible endpoint on any site. */
class CloudEngine(private val endpoint: CloudEndpoint) : AiEngine {

    override val id = "cloud:${endpoint.id}"
    override val displayName = "Cloud: ${endpoint.name} (${endpoint.model})"
    override val isReady get() = endpoint.baseUrl.isNotBlank() && endpoint.model.isNotBlank()

    @Volatile
    private var conn: HttpURLConnection? = null

    override fun cancel() {
        runCatching { conn?.disconnect() }
    }

    override suspend fun chat(messages: List<ChatMessage>, opts: GenOptions): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("model", endpoint.model)
                put("messages", JSONArray().apply {
                    messages.forEach {
                        put(JSONObject().put("role", it.role).put("content", it.content))
                    }
                })
                put("max_tokens", minOf(opts.maxTokens, endpoint.maxTokens))
                put("temperature", opts.temperature.toDouble())
                put("top_p", opts.topP.toDouble())
            }

            var lastErr: Exception? = null
            repeat(2) { attempt ->
                try {
                    val url = URL(endpoint.completionUrl())
                    val c = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 90_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        if (endpoint.apiKey.isNotBlank()) {
                            // "Bearer" works for OpenAI-compatible APIs; raw key kept too via custom headers
                            setRequestProperty("Authorization", "Bearer ${endpoint.apiKey}")
                        }
                        endpoint.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                        conn = this
                    }
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = c.responseCode
                    val resp = (if (code in 200..299) c.inputStream else c.errorStream)
                        ?.bufferedReader()?.readText() ?: ""
                    if (code !in 200..299) {
                        throw CloudException(code, resp.take(400))
                    }
                    val json = JSONObject(resp)
                    val content = json.optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")
                        ?.optString("content").orEmpty()
                    if (content.isEmpty() && attempt == 0) throw CloudException(code, "empty response")
                    return@withContext content
                } catch (e: CloudException) {
                    throw e
                } catch (e: Exception) {
                    lastErr = e
                    Log.w(TAG, "cloud attempt $attempt failed: ${e.message}")
                }
            }
            throw lastErr ?: IllegalStateException("cloud request failed")
        }

    companion object {
        const val TAG = "CloudEngine"
    }
}

class CloudException(val code: Int, val detail: String) :
    Exception("cloud API error $code: $detail")

/** Persists endpoint list as JSON inside Prefs. */
class EndpointStore(private val prefsRaw: () -> String, private val save: (String) -> Unit) {

    fun list(): List<CloudEndpoint> = runCatching {
        val arr = JSONArray(prefsRaw().ifBlank { "[]" })
        (0 until arr.length()).map { CloudEndpoint.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun upsert(e: CloudEndpoint) {
        val cur = list().filterNot { it.id == e.id } + e
        saveJson(cur)
    }

    fun remove(id: String) {
        saveJson(list().filterNot { it.id == id })
    }

    fun active(id: String): CloudEndpoint? = list().firstOrNull { it.id == id }

    private fun saveJson(list: List<CloudEndpoint>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        save(arr.toString())
    }
}

/** Helper for building query strings if a site needs them (kept for custom links). */
fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")
