// InfiKey native bridge — llama.cpp via JNI (streaming, cancellable).
#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "InfiKeyNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::atomic<bool> g_cancel{false};

struct Job {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
};

// Length of the prefix that ends on a whole UTF-8 character.
size_t completeUtf8Length(const std::string& s) {
    size_t i = s.size();
    for (int back = 1; back <= 4 && i > 0; ++back) {
        unsigned char c = (unsigned char)s[--i];
        if ((c & 0xC0) == 0x80) continue; // continuation byte, keep looking for the lead
        int need = (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 1;
        return back >= need ? s.size() : i;
    }
    return s.size();
}

// UTF-8 -> jstring via UTF-16 (safe for emoji / Indic text).
jstring utf8ToJString(JNIEnv* env, const std::string& utf8) {
    std::u16string out;
    size_t i = 0;
    while (i < utf8.size()) {
        unsigned char c = (unsigned char)utf8[i];
        uint32_t cp = 0; int len = 0;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
        else { cp = c & 0x07; len = 4; }
        for (int k = 1; k < len && i + k < utf8.size(); ++k)
            cp = (cp << 6) | ((unsigned char)utf8[i + k] & 0x3F);
        i += len;
        if (cp >= 0x10000) {
            cp -= 0x10000;
            out.push_back((char16_t)(0xD800 | (cp >> 10)));
            out.push_back((char16_t)(0xDC00 | (cp & 0x3FF)));
        } else {
            out.push_back((char16_t)cp);
        }
    }
    return env->NewString(reinterpret_cast<const jchar*>(out.data()), (jsize)out.size());
}

Job* asJob(jlong handle) { return reinterpret_cast<Job*>(handle); }

std::string jstrToStd(JNIEnv* env, jstring j) {
    if (!j) return std::string();
    const char* p = env->GetStringUTFChars(j, nullptr);
    std::string s(p ? p : "");
    env->ReleaseStringUTFChars(j, p);
    return s;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_infikey_ai_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jobject, jstring path, jint nCtx, jint nThreads, jint nGpuLayers) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = nGpuLayers;
    LOGI("loading model %s (ctx=%d threads=%d gpu=%d)", p, nCtx, nThreads, nGpuLayers);
    llama_model* model = llama_model_load_from_file(p, mparams);
    env->ReleaseStringUTFChars(path, p);
    if (!model) { LOGE("model load failed"); return 0; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t)nCtx;
    cparams.n_batch = (uint32_t)std::min<uint32_t>(2048, (uint32_t)nCtx);
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    cparams.no_perf = true;
    llama_context* ctx = llama_init_from_model(model, cparams);
    if (!ctx) { llama_model_free(model); LOGE("context init failed"); return 0; }

    auto* job = new Job{model, ctx, llama_model_get_vocab(model)};
    return reinterpret_cast<jlong>(job);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_infikey_ai_LlamaBridge_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jstring prompt, jint maxTokens,
        jfloat temperature, jfloat topP, jint topK, jstring stop, jobject callback) {
    Job* job = asJob(handle);
    if (!job || !callback) return JNI_FALSE;
    g_cancel = false;

    std::string promptStr = jstrToStd(env, prompt);
    std::string stopStr = jstrToStd(env, stop);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");
    if (!onToken) return JNI_FALSE;

    // each request is self-contained: drop what the previous one left in the context
    llama_memory_clear(llama_get_memory(job->ctx), true);

    // Tokenize
    std::vector<llama_token> tokens(promptStr.size() + 8);
    int n = llama_tokenize(job->vocab, promptStr.c_str(), (int32_t)promptStr.size(),
                           tokens.data(), (int32_t)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize((size_t)(-n));
        n = llama_tokenize(job->vocab, promptStr.c_str(), (int32_t)promptStr.size(),
                           tokens.data(), (int32_t)tokens.size(), true, true);
    }
    if (n <= 0) return JNI_FALSE;
    tokens.resize((size_t)n);
    LOGI("prompt tokens: %d", n);

    // Sampler chain (temp -> top-k -> top-p -> dist)
    llama_sampler* chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_temp_ext(temperature, 0.0f, 1.0f));
        if (topK > 0) llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK));
        if (topP < 1.0f) llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(-1));
    }

    std::string generated;
    std::string pending; // bytes of a character that is split across tokens
    llama_token tok = 0; // outlives the loop body: the next batch points at it
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    bool ok = true;
    for (int step = 0; step < maxTokens; ++step) {
        if (g_cancel) break;
        if (llama_decode(job->ctx, batch)) { ok = false; break; }
        tok = llama_sampler_sample(chain, job->ctx, -1);
        if (llama_vocab_is_eog(job->vocab, tok)) break;

        char buf[512];
        int n_chars = llama_detokenize(job->vocab, &tok, 1, buf, sizeof(buf), false, true);
        batch = llama_batch_get_one(&tok, 1);
        if (n_chars <= 0) continue;
        generated.append(buf, (size_t)n_chars);
        pending.append(buf, (size_t)n_chars);

        const size_t ready = completeUtf8Length(pending);
        if (ready == 0) continue;
        jstring js = utf8ToJString(env, pending.substr(0, ready));
        pending.erase(0, ready);
        jboolean cont = env->CallBooleanMethod(callback, onToken, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) { ok = false; break; }
        if (!cont) break;

        if (!stopStr.empty()) {
            size_t pos = generated.rfind(stopStr);
            if (pos != std::string::npos) break;
        }
    }
    llama_sampler_free(chain);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_infikey_ai_LlamaBridge_nativeCancel(JNIEnv*, jobject) {
    g_cancel = true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_infikey_ai_LlamaBridge_nativeModelInfo(JNIEnv* env, jobject, jlong handle) {
    Job* job = asJob(handle);
    if (!job) return nullptr;
    char desc[512] = {0};
    llama_model_desc(job->model, desc, sizeof(desc));
    char tpl[16384] = {0};
    int32_t got = llama_model_meta_val_str(job->model, "tokenizer.chat_template", tpl, sizeof(tpl));
    std::string json = std::string("{\"desc\":\"") + desc + "\",\"n_ctx_train\":" +
                       std::to_string(llama_model_n_ctx_train(job->model)) +
                       ",\"chat_template\":\"";
    if (got > 0) {
        for (const char* c = tpl; *c; ++c) {
            if (*c == '"') json += "\\\"";
            else if (*c == '\\') json += "\\\\";
            else if (*c == '\n') json += "\\n";
            else if (*c == '\r') json += "\\r";
            else if (*c == '\t') json += "\\t";
            else json += *c;
        }
    }
    json += "\"}";
    return utf8ToJString(env, json);
}

extern "C" JNIEXPORT void JNICALL
Java_com_infikey_ai_LlamaBridge_nativeFree(JNIEnv*, jobject, jlong handle) {
    Job* job = asJob(handle);
    if (!job) return;
    if (job->ctx) llama_free(job->ctx);
    if (job->model) llama_model_free(job->model);
    delete job;
}
