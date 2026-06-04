# AI Workbench

Admin tool for experimenting with OpenAI-powered PTV description generation prompts. Read-only — no database writes are made.

Accessible from the Admin dashboard tab (requires `:users/manage` privilege).

## Purpose

PTV (Palvelutietovaranto) is Finland's national service information repository. LIPAS integrates with PTV to publish sports facility information there. The AI Workbench lets admins iterate on the prompts and LLM parameters used to generate PTV-compliant descriptions in three languages (Finnish, Swedish, English), without affecting production data.

## Flows

### Service-Location

Generate descriptions for a **single sports facility**.

- **Input**: LIPAS ID
- Fetches the site from Elasticsearch, passes it through `ai/->prompt-doc` (declarative allowlist of citizen-relevant fields)
- Offers both the v3 (legacy) and v5 (current DVV-aligned) prompt templates for side-by-side comparison

### Service

Generate descriptions for an **entire service category** across multiple locations.

- **Input**: City codes + sub-category ID
- Queries PTV integration for eligible sites (city-owned), builds an aggregate overview
- Offers both v3 and v5 service-description prompts

Note: production generation uses **v5 prompts with Gemini 3 Flash**; the workbench uses OpenAI to give admins a fast iteration loop on prompt wording. The workbench does not exercise the batch-generation flow — that's for production use from the PTV wizard.

## LLM Parameters

| Parameter | Default | Range |
|-----------|---------|-------|
| model | gpt-4.1-mini | Any OpenAI model |
| top_p | 0.5 | 0–1 |
| presence_penalty | -2 | -2–2 |
| temperature | off (optional) | 0–2 |
| max_tokens | 4096 | — |
| n | 1 | 1–5 completions |

System and user prompts are editable in the UI. "Reset to defaults" restores server-side defaults.

## Response Format

Uses OpenAI Structured Outputs (JSON Schema, strict mode):

```json
{
  "description": {"fi": "...", "se": "...", "en": "..."},
  "summary":     {"fi": "...", "se": "...", "en": "..."}
}
```

## PTV Style Guidelines (v5, DVV-aligned)

The current v5 system prompt enforces:

- Factual, neutral language — no promotional or marketing phrases
- No exclamation marks; no evaluative adjectives ("monipuolinen", "kattava", "versatile", "extensive")
- No "tervetuloa", "nauttia", "welcome", "enjoy" etc.
- No street addresses, phone numbers, URLs, or organisation names as the subject
- Information order: What → Access → Facilities → Conditions
- Reflects facility `status` (out-of-service states must be stated explicitly)
- Summary max 150 characters, description max 2000 characters, user-instruction max 2500 characters per language
- Finnish-specific rules for place-name inflection and verb variety

The workbench still exposes v3 alongside v5 for comparison.

## Architecture

### Frontend

All under `src/cljs/lipas/ui/admin/ai_workbench/`:

| File | Purpose |
|------|---------|
| `views.cljs` | UI components (Reagent 2.0 + MUI) |
| `events.cljs` | Re-frame event handlers |
| `subs.cljs` | Re-frame subscriptions |

State path: `[:admin :ai-workbench]`

### Backend

| File | Purpose |
|------|---------|
| `src/clj/lipas/backend/ptv/workbench.clj` | HTTP handlers for preview-data and experiment endpoints |
| `src/clj/lipas/backend/ptv/ai.clj` | Prompt templates, OpenAI API calls, system instructions |

### API Endpoints

Both require `:users/manage` privilege.

- `POST /actions/ptv-workbench/preview-data` — Fetch prompt data for a flow + inputs
- `POST /actions/ptv-workbench/experiment` — Run OpenAI completion with given prompts and params

### Configuration

OpenAI settings in `config.clj`, reading from environment:

```clojure
:open-ai
{:api-key (env! :open-ai-api-key)
 :completions-url "https://api.openai.com/v1/chat/completions"
 :model "gpt-4.1-mini"}
```
