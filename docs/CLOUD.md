# CLOUD.md — connecting any cloud API

InfiKey talks to **any OpenAI-compatible chat endpoint on any site**. Not a curated
provider list — literally any base URL that speaks the `/chat/completions` dialect:
Groq, OpenRouter, Together, Fireworks, DeepSeek, your own server, or local runtimes on
your network like Ollama and LM Studio.

## Adding an endpoint

**InfiKey Settings → AI → Cloud endpoints → Add**, then fill in:

| Field | Meaning | Example |
| --- | --- | --- |
| Name | Label shown in the picker | `Groq Llama` |
| Base URL | Any link the provider gives you | `https://api.groq.com/openai/v1` |
| API key | Sent as a Bearer token (leave blank for local servers) | `gsk_…` |
| Model | The model id the provider expects | `llama-3.3-70b-versatile` |
| Extra headers | For exotic providers that need odd headers | `HTTP-Referer: https://…` |
| Max tokens | Per-endpoint output cap (default 600) | `600` |

Multiple endpoints can coexist; you pick the active one for the DEEP tier
(see *Routing* below). Endpoints are stored as JSON in app-private preferences —
the API key never leaves the device except to the endpoint you configured.

## URL normalization — any link works

You can paste almost any URL shape a provider hands you; the client (`CloudEngine.completionUrl()`)
fixes it up:

| You paste | Request goes to |
| --- | --- |
| `https://api.x.com/v1/chat/completions` | unchanged |
| `https://api.x.com/v1` | `https://api.x.com/v1/chat/completions` |
| `https://api.x.com/v2` | `https://api.x.com/v2/chat/completions` |
| `https://api.x.com/openai` | `https://api.x.com/openai/v1/chat/completions` |
| `https://api.x.com` (bare host) | `https://api.x.com/v1/chat/completions` |

## Copy-paste examples

**Groq** — base `https://api.groq.com/openai/v1`, key from console.groq.com,
model `llama-3.3-70b-versatile`.

**OpenRouter** — base `https://openrouter.ai/api/v1`, key from openrouter.ai/keys,
model e.g. `anthropic/claude-3.5-sonnet`. If the site asks for it, add headers
`HTTP-Referer` and `X-Title`.

**Together** — base `https://api.together.xyz/v1`, model e.g. `meta-llama/Llama-3-8b-chat-hf`.

**Ollama on your PC** (same Wi-Fi) — base `http://192.168.1.5:11434/v1`
(your PC's LAN IP), no key, model e.g. `qwen2.5:7b`. Start Ollama with
`OLLAMA_HOST=0.0.0.0` so it accepts LAN connections.

**LM Studio on your PC** — start the server (default port 1234), base
`http://192.168.1.5:1234/v1`, no key, model name as shown in LM Studio.

## How requests are made

`CloudEngine.chat()` sends a plain OpenAI-style POST:

```json
{
  "model": "<endpoint model>",
  "messages": [{"role": "system", "content": "…"}, {"role": "user", "content": "…"}],
  "max_tokens": 600,
  "temperature": 0.8,
  "top_p": 0.9
}
```

- `Authorization: Bearer <key>` is added when a key is set; **custom headers can add or
  override anything** (auth schemes that aren't Bearer, provider metadata, etc.).
- Timeouts: 15 s connect, 90 s read — long generations don't get cut off.
- **Two attempts** are made automatically before an error surfaces.
- Errors bubble up as `cloud API error <code>: <detail>` with the server's message, so
  quota/permission problems are readable in the AI panel.

## Routing: fast vs deep

`AiRouter` separates work into two tiers:

- **FAST tier** — quick jobs (autocorrect-style fixes, chips) run on the on-device model.
- **DEEP tier** — full generation (essays, rewrites, chat) runs on your chosen deep
  engine: a big imported GGUF **or** a cloud endpoint.

The `ai_mode` preference controls auto vs forced selection, so you can keep AI fully
offline during the day and flip to cloud only when you want it.

## Privacy

Text is sent over the network **only** when you actually use a cloud endpoint — every
request is recorded in the AI transparency log (feature 57), and apps on your **safe-app
list** (feature 58) are never read at all. Details in [PRIVACY.md](PRIVACY.md).
