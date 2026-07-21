# Gemini 3 Flash Preview — Structured Output & Thinking API Reference

> Source: Official Google AI / Vertex AI documentation, retrieved 2026-03-14

---

## Model

| Property | Value |
|---|---|
| Model ID | `gemini-3-flash-preview` |
| Status | Preview |
| Input token limit | 1,048,576 |
| Output token limit | 65,536 |
| Knowledge cutoff | January 2025 |
| Pricing | $0.50 / 1M input tokens, $3.00 / 1M output tokens |

---

## Structured Output

### Configuration

Set two fields in `generationConfig`:

- **`responseMimeType`**: `"application/json"`
- **`responseJsonSchema`**: a JSON Schema object

```json
{
  "generationConfig": {
    "responseMimeType": "application/json",
    "responseJsonSchema": {
      "type": "object",
      "properties": {
        "recipe_name": { "type": "string" },
        "ingredients": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "name":     { "type": "string" },
              "quantity": { "type": "string" }
            }
          }
        }
      },
      "required": ["recipe_name", "ingredients"]
    }
  }
}
```

### Supported JSON Schema Types

`string`, `number`, `integer`, `boolean`, `object`, `array`, `null`

Supported schema keywords: `description`, `enum`, `required`, `properties`

### Guarantees & Limitations

- Guarantees **syntactically** valid JSON matching the schema; does **not** guarantee semantic correctness of values.
- Property order follows schema key sequence.
- Very large or deeply nested schemas may be rejected.
- Unsupported JSON Schema keywords are silently ignored.
- Streaming is supported — partial JSON chunks are concatenable into a complete object.

### REST Example

```bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent" \
  -H "x-goog-api-key: $GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -X POST \
  -d '{
    "contents": [{"parts": [{"text": "Extract recipe details from: ..."}]}],
    "generationConfig": {
      "responseMimeType": "application/json",
      "responseJsonSchema": {
        "type": "object",
        "properties": {
          "recipe_name": {"type": "string"},
          "ingredients": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "name":     {"type": "string"},
                "quantity": {"type": "string"}
              }
            }
          }
        }
      }
    }
  }'
```

---

## Thinking

### Configuration

Gemini 3 uses `thinkingLevel` (a categorical enum). The numeric `thinkingBudget` parameter from Gemini 2.5 is **not supported** — using both in the same request returns an error.

| `thinkingLevel` | Behavior |
|---|---|
| `minimal` | Default. Almost no thinking; may think slightly for complex coding. Lowest latency. |
| `low` | Minimized latency and cost. Good for simple instruction following. |
| `medium` | Balanced reasoning. |
| `high` | Maximum reasoning depth. Higher latency, more careful results. |

Enable thought summaries in the response with `includeThoughts: true`:

```python
from google import genai
from google.genai import types

client = genai.Client()
response = client.models.generate_content(
    model="gemini-3-flash-preview",
    contents="What is the sum of the first 50 prime numbers?",
    config=types.GenerateContentConfig(
        thinking_config=types.ThinkingConfig(
            include_thoughts=True,
            thinking_level="high"
        )
    )
)

for part in response.candidates[0].content.parts:
    if part.thought:
        print("Thought:", part.text)
    else:
        print("Answer:", part.text)
```

---

## Response Structure

`GenerateContentResponse` top-level fields:

```
candidates[]        — list of Candidate
promptFeedback      — safety/prompt feedback
usageMetadata       — token counts
modelVersion        — string
responseId          — string
```

`candidates[0].content.parts` is an **array of Part objects**. Each Part can have:

| Field | Type | Description |
|---|---|---|
| `text` | string | The text content of this part |
| `thought` | boolean | `true` = thinking block; `false`/absent = final answer |
| `thoughtSignature` | string | Encrypted reasoning state (see below) |
| `functionCall` | object | Function invocation (name + args) |
| `inlineData` | object | `mime_type` + base64 `data` |
| `fileData` | object | `mime_type` + `file_uri` |

### With `includeThoughts: true`

The parts array will contain **one or more thought parts** followed by **one text part** (the answer):

```
parts[0]  → { thought: true,  text: "I need to consider..." }
parts[1]  → { thought: false, text: "<final answer>" }
```

Without streaming: a single thought summary is returned.
With streaming: incremental thought summaries are delivered during generation.

### Token Usage

`usageMetadata` includes:

| Field | Description |
|---|---|
| `promptTokenCount` | Input tokens |
| `candidatesTokenCount` | Output tokens |
| `thoughtsTokenCount` | Tokens consumed by thinking |
| `totalTokenCount` | Combined total |

---

## Thought Signatures

Thought signatures are **encrypted representations of the model's internal reasoning state**. They are used to preserve the reasoning context across multi-turn conversations and agentic tool-call loops.

### Where They Appear

On `functionCall` parts:

```json
{
  "functionCall": {
    "name": "check_flight",
    "args": { "flight": "AA100" }
  },
  "thoughtSignature": "<encrypted_signature>"
}
```

On text/other parts (optional but recommended):

```json
{
  "text": "Based on my analysis...",
  "thoughtSignature": "<encrypted_signature>"
}
```

### Rules for Gemini 3

- **Mandatory for function calls**: The first `functionCall` part in each turn must include its `thoughtSignature` when returned to the model. Omitting it causes a `400` error:
  `"Function call <name> in the <index> content block is missing a thought_signature"`
- For parallel function calls: only the *first* call gets a signature; the rest have none.
- For text parts: signatures are optional but improve reasoning quality if included.
- **Official SDKs handle signatures automatically** — manual management is only needed with raw REST calls.

### Multi-Turn Conversation Structure

```json
{
  "contents": [
    { "role": "user",  "parts": [{ "text": "Check my flight AA100" }] },
    {
      "role": "model",
      "parts": [{
        "functionCall": { "name": "check_flight", "args": { "flight": "AA100" } },
        "thoughtSignature": "<Sig A>"
      }]
    },
    { "role": "user", "parts": [{ "functionResponse": { "name": "check_flight", "response": { ... } } }] },
    {
      "role": "model",
      "parts": [{
        "functionCall": { "name": "book_taxi", "args": { ... } },
        "thoughtSignature": "<Sig B>"
      }]
    }
  ]
}
```

---

## Combining Structured Output + Thinking

Both features can be used simultaneously. When combined:

1. Send `responseMimeType` + `responseJsonSchema` in `generationConfig`
2. Send `ThinkingConfig` with `includeThoughts` and `thinkingLevel`
3. The response parts will contain thought parts (reasoning summary) followed by a final text part containing the JSON conforming to your schema

### Known Issue

Combining structured output + `ThinkingConfig` + **File Search** can result in a nil response, empty `groundingMetadata.groundingChunks`, and abnormally high `toolUsePromptTokenCount`. Avoid this combination until resolved.

---

## Important Gotchas

- **Temperature**: Keep at default `1.0`. Lowering it can cause looping or degraded performance — this is a known Gemini 3 behaviour change from earlier models.
- **`thinkingBudget` is Gemini 2.5 only**: Using it with Gemini 3 or mixing it with `thinkingLevel` returns an error.
- **Thought signatures are mandatory on function calls in Gemini 3**: Unlike Gemini 2.5 where they were optional.

---

## Sources

- [Gemini 3 Flash Preview — Model Card](https://ai.google.dev/gemini-api/docs/models/gemini-3-flash-preview)
- [Structured Outputs](https://ai.google.dev/gemini-api/docs/structured-output)
- [Gemini Thinking](https://ai.google.dev/gemini-api/docs/thinking)
- [Thought Signatures](https://ai.google.dev/gemini-api/docs/thought-signatures)
- [Gemini 3 Developer Guide](https://ai.google.dev/gemini-api/docs/gemini-3)
- [Vertex AI — Thinking](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/thinking)
- [Vertex AI — Gemini 3 Flash](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/3-flash)
