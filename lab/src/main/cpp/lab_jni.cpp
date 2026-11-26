// JNI bridge for the M0 lab: one-shot ASR and LLM benchmark runs.
//
// Every call returns a UTF-8 JSON document as a Java byte[] rather than a
// jstring: NewStringUTF expects *modified* UTF-8 and aborts the process on some
// valid UTF-8 input, and model output is arbitrary UTF-8 (Czech diacritics,
// emoji, and token pieces that split a character in half).

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <string>
#include <vector>

#include "llama.h"
#include "parakeet.h"
#include "whisper.h"

#define TAG "summarised-lab"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

std::atomic<bool> g_cancel{false};

double now_ms() {
    using namespace std::chrono;
    return duration<double, std::milli>(steady_clock::now().time_since_epoch()).count();
}

void json_escape_into(std::string & out, const std::string & s) {
    out.push_back('"');
    for (unsigned char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out.push_back(static_cast<char>(c));  // UTF-8 passes through
                }
        }
    }
    out.push_back('"');
}

// Tiny JSON object builder; enough for flat results plus one array.
struct Json {
    std::string s = "{";
    bool first = true;
    void key(const char * k) {
        if (!first) s.push_back(',');
        first = false;
        json_escape_into(s, k);
        s.push_back(':');
    }
    Json & str(const char * k, const std::string & v) { key(k); json_escape_into(s, v); return *this; }
    Json & num(const char * k, double v) { key(k); char b[64]; snprintf(b, sizeof(b), "%.3f", v); s += b; return *this; }
    Json & integer(const char * k, long long v) { key(k); s += std::to_string(v); return *this; }
    Json & boolean(const char * k, bool v) { key(k); s += v ? "true" : "false"; return *this; }
    Json & raw(const char * k, const std::string & v) { key(k); s += v; return *this; }
    std::string done() { return s + "}"; }
};

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

std::string from_bytes(JNIEnv * env, jbyteArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(s.data()));
    return s;
}

std::string from_jstring(JNIEnv * env, jstring js) {
    const char * c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

std::string error_json(const std::string & msg) {
    return Json().boolean("ok", false).str("error", msg).done();
}

void quiet_log(enum ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

bool abort_cb(void *) { return g_cancel.load(); }

}  // namespace

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_io_github_fush1ft_summarised_lab_bench_Native_systemInfo(JNIEnv * env, jobject) {
    return to_bytes(env, Json()
        .str("llama", llama_print_system_info())
        .str("whisper", whisper_print_system_info())
        .done());
}

JNIEXPORT void JNICALL
Java_io_github_fush1ft_summarised_lab_bench_Native_cancel(JNIEnv *, jobject) {
    g_cancel.store(true);
}

// engine: 0 = Whisper, 1 = Parakeet. samples: 16 kHz mono float PCM.
JNIEXPORT jbyteArray JNICALL
Java_io_github_fush1ft_summarised_lab_bench_Native_transcribe(
        JNIEnv * env, jobject, jint engine, jstring jmodel, jfloatArray jsamples,
        jint threads, jstring jlang) {
    g_cancel.store(false);
    const std::string model = from_jstring(env, jmodel);
    const std::string lang = from_jstring(env, jlang);
    const jsize n = env->GetArrayLength(jsamples);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(jsamples, 0, n, pcm.data());

    std::string segments = "[";
    std::string text;
    Json out;

    if (engine == 0) {
        whisper_log_set(quiet_log, nullptr);
        const double t_load = now_ms();
        whisper_context_params cp = whisper_context_default_params();
        cp.use_gpu = false;
        cp.flash_attn = true;
        whisper_context * ctx = whisper_init_from_file_with_params(model.c_str(), cp);
        if (ctx == nullptr) return to_bytes(env, error_json("whisper: failed to load model"));
        const double load_ms = now_ms() - t_load;

        whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        p.n_threads = threads;
        p.language = lang.c_str();
        p.detect_language = false;
        p.translate = false;
        p.print_progress = false;
        p.print_realtime = false;
        p.print_special = false;
        p.print_timestamps = false;
        p.suppress_nst = true;
        p.abort_callback = abort_cb;

        const double t_run = now_ms();
        const int rc = whisper_full(ctx, p, pcm.data(), static_cast<int>(pcm.size()));
        const double run_ms = now_ms() - t_run;
        if (rc != 0) {
            whisper_free(ctx);
            return to_bytes(env, error_json(g_cancel.load() ? "cancelled" : "whisper_full failed"));
        }
        const int ns = whisper_full_n_segments(ctx);
        for (int i = 0; i < ns; ++i) {
            const std::string t = whisper_full_get_segment_text(ctx, i);
            text += t;
            if (i) segments.push_back(',');
            segments += Json()
                .integer("t0", whisper_full_get_segment_t0(ctx, i) * 10)
                .integer("t1", whisper_full_get_segment_t1(ctx, i) * 10)
                .str("text", t).done();
        }
        whisper_timings * tm = whisper_get_timings(ctx);
        out.num("load_ms", load_ms).num("run_ms", run_ms);
        if (tm) out.num("encode_ms", tm->encode_ms).num("decode_ms", tm->decode_ms);
        whisper_free(ctx);
    } else {
        parakeet_log_set(quiet_log, nullptr);
        const double t_load = now_ms();
        parakeet_context_params cp = parakeet_context_default_params();
        cp.use_gpu = false;
        parakeet_context * ctx = parakeet_init_from_file_with_params(model.c_str(), cp);
        if (ctx == nullptr) return to_bytes(env, error_json("parakeet: failed to load model"));
        const double load_ms = now_ms() - t_load;

        parakeet_full_params p = parakeet_full_default_params(PARAKEET_SAMPLING_GREEDY);
        p.n_threads = threads;
        p.abort_callback = abort_cb;

        const double t_run = now_ms();
        const int rc = parakeet_full(ctx, p, pcm.data(), static_cast<int>(pcm.size()));
        const double run_ms = now_ms() - t_run;
        if (rc != 0) {
            parakeet_free(ctx);
            return to_bytes(env, error_json(g_cancel.load() ? "cancelled" : "parakeet_full failed"));
        }
        const int ns = parakeet_full_n_segments(ctx);
        for (int i = 0; i < ns; ++i) {
            const std::string t = parakeet_full_get_segment_text(ctx, i);
            if (!text.empty() && !t.empty() && t[0] != ' ') text.push_back(' ');
            text += t;
            if (i) segments.push_back(',');
            segments += Json()
                .integer("t0", parakeet_full_get_segment_t0(ctx, i) * 10)
                .integer("t1", parakeet_full_get_segment_t1(ctx, i) * 10)
                .str("text", t).done();
        }
        parakeet_timings * tm = parakeet_get_timings(ctx);
        out.num("load_ms", load_ms).num("run_ms", run_ms);
        if (tm) out.num("encode_ms", tm->encode_ms).num("decode_ms", tm->decode_ms);
        parakeet_free(ctx);
    }

    segments.push_back(']');
    out.boolean("ok", true)
       .num("audio_ms", 1000.0 * static_cast<double>(pcm.size()) / 16000.0)
       .str("text", text)
       .raw("segments", segments);
    return to_bytes(env, out.done());
}

// Loads the model, applies its chat template, prefills the prompt and generates.
// Each generated piece is passed to listener.onToken(byte[]) as raw UTF-8.
JNIEXPORT jbyteArray JNICALL
Java_io_github_fush1ft_summarised_lab_bench_Native_generate(
        JNIEnv * env, jobject, jstring jmodel, jbyteArray jsystem, jbyteArray juser,
        jint n_ctx, jint threads, jint max_tokens, jfloat temperature, jobject listener) {
    g_cancel.store(false);
    const std::string model_path = from_jstring(env, jmodel);
    const std::string system = from_bytes(env, jsystem);
    const std::string user = from_bytes(env, juser);

    jclass lcls = env->GetObjectClass(listener);
    jmethodID on_token = env->GetMethodID(lcls, "onToken", "([B)V");

    llama_log_set(quiet_log, nullptr);
    llama_backend_init();

    const double t_load = now_ms();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(model_path.c_str(), mp);
    if (model == nullptr) return to_bytes(env, error_json("llama: failed to load model"));

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(n_ctx);
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    cp.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        llama_model_free(model);
        return to_bytes(env, error_json("llama: failed to create context (out of memory?)"));
    }
    const double load_ms = now_ms() - t_load;

    char desc[256] = {0};
    llama_model_desc(model, desc, sizeof(desc));
    const llama_vocab * vocab = llama_model_get_vocab(model);

    // Chat template from the GGUF, falling back to ChatML.
    std::vector<llama_chat_message> msgs;
    if (!system.empty()) msgs.push_back({"system", system.c_str()});
    msgs.push_back({"user", user.c_str()});
    const char * tmpl = llama_model_chat_template(model, nullptr);
    std::vector<char> buf(2 * (system.size() + user.size()) + 1024);
    int32_t len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    if (len > static_cast<int32_t>(buf.size())) {
        buf.resize(static_cast<size_t>(len));
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (len < 0) {
        len = llama_chat_apply_template("chatml", msgs.data(), msgs.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (len < 0) {
        llama_free(ctx);
        llama_model_free(model);
        return to_bytes(env, error_json("llama: could not apply a chat template"));
    }
    const std::string prompt(buf.data(), static_cast<size_t>(len));

    const int n_prompt = -llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    std::vector<llama_token> tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), n_prompt, true, true) < 0 ||
        n_prompt + max_tokens > n_ctx) {
        llama_free(ctx);
        llama_model_free(model);
        return to_bytes(env, error_json("prompt of " + std::to_string(n_prompt) + " tokens does not fit the context"));
    }

    llama_batch_ext * batch = llama_batch_ext_init(ctx);
    const int n_batch = static_cast<int>(llama_n_batch(ctx));

    // Prefill in n_batch-sized chunks; logits only for the very last prompt token.
    const double t_prompt = now_ms();
    bool failed = false;
    for (int start = 0; start < n_prompt && !failed; start += n_batch) {
        const int count = std::min(n_batch, n_prompt - start);
        llama_batch_ext_clear(batch);
        for (int i = 0; i < count; ++i) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[static_cast<size_t>(start + i)]);
            const llama_pos pos = start + i;
            llama_batch_ext_set_pos(batch, idx, &pos);
        }
        if (start + count == n_prompt) llama_batch_ext_set_output_logits(batch, count - 1, true);
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) failed = true;
        if (g_cancel.load()) failed = true;
    }
    const double prompt_ms = now_ms() - t_prompt;

    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(0, 64, 1.1f, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string text;
    int n_gen = 0;
    const double t_gen = now_ms();
    llama_pos pos = n_prompt;
    while (!failed && n_gen < max_tokens && !g_cancel.load()) {
        const llama_token tok = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        char piece[256];
        const int np = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, false);
        if (np > 0) {
            text.append(piece, static_cast<size_t>(np));
            jbyteArray jp = env->NewByteArray(np);
            env->SetByteArrayRegion(jp, 0, np, reinterpret_cast<const jbyte *>(piece));
            env->CallVoidMethod(listener, on_token, jp);
            env->DeleteLocalRef(jp);
            if (env->ExceptionCheck()) { env->ExceptionClear(); }
        }
        ++n_gen;

        llama_batch_ext_clear(batch);
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tok);
        llama_batch_ext_set_pos(batch, idx, &pos);
        llama_batch_ext_set_output_logits(batch, idx, true);
        ++pos;
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) failed = true;
    }
    const double gen_ms = now_ms() - t_gen;

    llama_sampler_free(smpl);
    llama_batch_ext_free(batch);
    llama_free(ctx);
    llama_model_free(model);

    if (failed && n_gen == 0) return to_bytes(env, error_json(g_cancel.load() ? "cancelled" : "llama: decode failed"));

    return to_bytes(env, Json()
        .boolean("ok", true)
        .boolean("cancelled", g_cancel.load())
        .str("model", desc)
        .num("load_ms", load_ms)
        .integer("prompt_tokens", n_prompt)
        .num("prompt_ms", prompt_ms)
        .integer("gen_tokens", n_gen)
        .num("gen_ms", gen_ms)
        .str("text", text)
        .done());
}

}  // extern "C"
