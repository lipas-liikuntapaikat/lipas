# How AI Works in the LIPAS PTV Integration

## What is PTV?

PTV (Palvelutietovaranto) is Finland's national Service Information Repository — a central register where municipalities publish information about their public services, including sports facilities. Each facility needs descriptions in Finnish, Swedish, and English.

## The Problem

Writing descriptions for hundreds of sports facilities in three languages is time-consuming manual work. Each description must follow strict official guidelines: neutral administrative tone, no marketing language, concise factual content.

## How AI Helps

LIPAS uses an AI language model (Google Gemini 3 Flash, with OpenAI as an automatic fallback) to **draft facility descriptions automatically**. Here's how the process works:

1. **The AI reads a curated slice of the facility data** — facility name, type, location, free-text curator comments, and a tight set of citizen-relevant amenities (toilet, changing rooms, surface material, lighting, etc.). Technical specs that aren't useful for a service-finder page (exact dimensions, construction year, contact info) are filtered out before sending.

2. **The AI writes a draft description** — following strict rules about tone and style. It produces a short summary (max 150 characters), a longer description (max 2000 characters), and a user instruction (toimintaohje — how to access the service, max 2500 characters) in all three languages (Finnish, Swedish, English).

3. **A human reviews and edits** — the AI-generated text is always presented as a suggestion. Staff can edit the text before it gets published to PTV. The system marks AI-generated content so users know what was written by AI.

4. **Translation support** — if a facility already has a description in one language, the AI can translate it to the other two languages, keeping the original meaning intact.

5. **Batch generation for uniformity** — when a municipality generates descriptions for many same-type facilities at once (e.g., 20 ball fields), the AI processes them together in chunks of 10. This keeps structure, vocabulary, and sentence patterns aligned across sites so they read consistently when displayed side-by-side on municipality pages.

6. **Service-level descriptions** — beyond individual facilities, the AI can also generate descriptions for entire categories of services (e.g., "swimming halls" as a service type), using aggregated data from multiple facilities.

## What the AI Does NOT Do

- It does **not** make decisions about which facilities to publish
- It does **not** send anything to PTV automatically — a human always approves
- It does **not** invent information — it works only with data already in LIPAS
- It does **not** use promotional or marketing language — it follows strict administrative writing guidelines

## In Summary

The AI acts as a **writing assistant**: it takes structured facility data from LIPAS, turns it into properly formatted multilingual text drafts, and presents them for human review before publication to PTV. This saves significant time while ensuring consistent quality across all facility descriptions.

---

## Appendix: Prompts, Data, and Real-Life Examples

### Flow 1: Service Location Descriptions (individual facilities)

This flow generates descriptions for **individual sports facilities** (e.g., "Liminka swimming hall", "Utajärvi ice rink").

**What triggers it:** A user clicks "Generate description" for a specific facility in the PTV tools view, or re-generates one from the single-site editor. Batch generation for many facilities at once uses a dedicated batch flow (see Flow 1b below).

#### System Prompt

Every AI request starts with a **system instruction** that defines the AI's role and tone. The current version (v5, DVV-aligned) enforces:

- Administrative, neutral, unemotional tone; present tense; passive voice preferred
- Write only verifiable facts from the source data — never infer, never add
- **NO** promotional or welcoming phrases ("tervetuloa", "nauttia", "welcome", "enjoy")
- **NO** evaluative adjectives ("monipuolinen", "kattava", "versatile", "extensive")
- **NO** exclamation marks, emotional appeals, inviting phrases
- **NO** street addresses, phone numbers, URLs, or organisation names as subject (PTV has separate structured fields for these)
- Reflect the `status` field — mark facilities as permanently/temporarily out of service when that's their status
- 2–4 paragraphs, maximum 4 sentences per paragraph, one topic per paragraph
- Finnish-specific rules for place-name inflection, compound place names, and varying verb usage

#### User Prompt

The v5 user prompt adds a pre-write checklist:

> Create a summary and description for the Service Information Repository (Palvelutietovaranto).
>
> BEFORE WRITING, verify:
> 1. STATUS — is it "active", "out-of-service-permanently", or other? Reflect this.
> 2. FACTS — write ONLY what is explicitly in the data. Do not infer or invent.
> 3. ADDRESS — do NOT include any street address, phone number, or URL.
> 4. ORGANIZATION — do NOT repeat the organization/municipality name as subject.
>
> Summary: A complete sentence (not a list!) capturing the essential service. Max 150 chars/language.
>
> Description: 2–4 paragraphs in the order: what → access → facilities → conditions. Include a brief usage instruction. Max 2000 chars/language.

The facility's curated JSON data is appended directly after this text.

#### What Data Goes INTO the AI

The `->prompt-doc` function (in `lipas.backend.ptv.ai`) uses **declarative allowlists** — only explicitly listed fields pass through to the AI. Anything else is dropped, including any new fields added to the data model in the future (safe-by-default).

**Top-level fields included:**
- Facility `name` and `marketing-name`
- `comment` (free-text curator notes — often the best source of genuine detail)
- `status` (so the AI can reflect out-of-service state)
- `reservations-link` (presence signals "reservation needed"; the URL itself is not written into the description)
- `lipas-id` (for matching batch responses)
- `location.city.neighborhood` (only; street addresses and postal info are excluded)
- Resolved type name and municipality name from `search-meta` (multilingual)

**Properties included** (a tight ~20-field allowlist):
- Access flags: `free-use?`, `school-use?`, `year-round-use?`
- Amenities: `toilet?`, `changing-rooms?`, `shower?`, `sauna?`, `kiosk?`, `pier?`
- Surface & lighting: `surface-material`, `surface-material-info`, `ligthing?` (sic), `lighting-info`
- Accessibility: `accessibility-info`
- Citizen-actionable dimensions: `route-length-km`, `lit-route-length-km`, `track-length-m`, `pool-length-m`, `beach-length-m`
- Activity-indicator counts: `swimming-pool-count`, `ice-rinks-count`, `holes-count`, and `*-fields-count` for basketball, volleyball, tennis, badminton, floorball, handball, football
- Encoded: `stand-capacity-person` is bucketed to `:small`/`:medium`/`:large` (exact seat numbers can be misleading)

**Explicitly dropped** because feedback showed they cluttered descriptions without helping citizens: `area-m2`, `field-length-m`/`field-width-m`, `height-m` (ceiling), pool depths, `construction-year`, `renovation-years`, `email`/`phone-number`/`www`, administrative/publishing flags, technical specs (loudspeakers, scoreboard, etc.), all certifications, all geometry/images/videos/internal IDs.

Blank values (nil, empty strings, empty collections) are filtered out, and the previously-generated PTV descriptions are excluded to prevent the AI echoing its own prior output.

#### What Comes OUT of the AI

A structured JSON object validated against a JSON Schema (via the model's native structured-output feature). Three fields, each in three languages (Finnish, Swedish, English):

1. **summary** (max 150 characters per language) — a single complete sentence capturing what the facility is
2. **description** (max 2000 characters per language) — 2–4 paragraphs in the topic order: what → access → facilities → conditions
3. **user-instruction** (max 2500 characters per language, "toimintaohje") — 1–3 sentences telling the citizen how to access or start using the service

#### Real Example: Input Data

After `->prompt-doc` filtering, a sand-surfaced ball field in Raahe (lipas-id 83622) reduces to roughly this JSON:

```json
{
  "name": "Pattasten hiekkakenttä",
  "comment": "Kenttä on yleisesti pesäpallokäytössä. Rajat maalataan tarvittaessa.",
  "status": "active",
  "reservations-link": "https://tilavaraus.raahe.fi/WebTimmi/#/",
  "lipas-id": 83622,
  "location": { "neighborhood": "Pattanen" },
  "search-meta": {
    "type": {
      "name": { "fi": "Pallokenttä", "se": "Bollplan", "en": "Ball field" },
      "sub-category": { "fi": "Pallokentät", "se": "Bollplaner", "en": "Ball games courts" }
    },
    "location": { "city": { "name": { "fi": "Raahe", "se": "Brahestad", "en": "Raahe" } } }
  },
  "properties": {
    "surface-material": ["sand"],
    "free-use?": true,
    "school-use?": true,
    "toilet?": true,
    "changing-rooms?": true
  }
}
```

Compared with the raw ES document, **dropped before sending**: address, postal code, postal office, email, phone, website, admin, owner, construction-year, `field-length-m` (85), `field-width-m` (40), `area-m2` (3400), map coordinates, geometries, simple-geoms, audit info, type-code, province/avi-area hierarchy. The AI sees only what a citizen reading a service-finder page could act on.

#### Real Example: AI Output

**Summary (fi):**

> Pattasten hiekkakenttä on Pattasissa sijaitseva monipuolinen pallokenttä, jossa on pukuhuoneet ja wc-tilat.

**Description (fi):**

> Pattasten hiekkakenttä on hiekkapintainen liikuntapaikka Raahen Pattasissa. Kenttä on yleisesti pesäpallokäytössä, ja sen rajat maalataan tarvittaessa.
>
> Kenttä on avoinna vapaaseen käyttöön ja se palvelee myös koulun liikuntatunteja. Liikuntapaikalla on pukuhuoneet ja wc-tilat.
>
> Kenttää voi käyttää itsenäisesti tai varata vuoroja järjestettyyn toimintaan. Liikuntapaikka on käytettävissä lumettomana aikana.

**User instruction (fi):**

> Kenttä on vapaasti käytettävissä silloin, kun siellä ei ole varattuja vuoroja. Varauksia voi tehdä sähköisen varausjärjestelmän kautta.

Notice how the AI:
- Picked up the facility type, city name, neighborhood, and amenities (changing rooms, toilet) from the structured data
- Wove the curator's comment (pesäpallokäyttö, painted boundaries) naturally into the description
- Referenced electronic reservations because `reservations-link` was present — without quoting the URL itself
- Described the facility without exact dimensions (no "85 × 40 m" or "3400 m²"), even though that data exists in LIPAS
- Used neutral, factual language throughout

---

### Flow 1b: Batch Generation (many facilities at once)

Added to address feedback that descriptions for many same-type facilities (e.g., 20 ball fields in one municipality) looked inconsistent when displayed side-by-side on municipality pages — each one had a different structure because they were generated independently.

**What triggers it:** A user clicks "Generate descriptions" in the PTV wizard for a batch of facilities.

**How it works:**

1. The frontend groups the facilities by `type-code` and chunks each group into batches of **10**. A larger facility type (say, 23 ball fields) becomes three batches; different types are kept in separate batches.
2. Each batch is sent as a single call to `/actions/generate-ptv-descriptions-batch`, which asks Gemini to generate all 10 descriptions together. Generating them together naturally aligns structure, vocabulary, and sentence patterns.
3. For a type with multiple batches, the first successful description from batch 1 is passed as a **style reference** to subsequent batches, so continuity holds across partitions.
4. The response is validated against a JSON Schema (`{sites: [{lipas-id, summary, description, user-instruction}, ...]}`) using Gemini's structured-output feature. This prevents invalid JSON on long outputs.
5. If a batch returns fewer sites than requested (the model occasionally skips one), the missing ids are automatically retried in a smaller follow-up batch — up to 2 retry passes. Sites that still don't come back are flagged as failed.
6. The frontend merges results into app-db as each batch completes and shows progress against the total.

**Why batches of 10:** spike testing showed Gemini 3 Flash reliably handles up to ~25 sites per call for this prompt, but above that it sometimes bails early with a stub response. 10 leaves plenty of margin and keeps per-batch latency around 30–80 seconds.

**Why side-by-side quality matters:** the batch prompt explicitly tells the AI to use the **same topic order, sentence patterns, and vocabulary** across all facilities in the batch — but to **adapt the description length to each facility's data density**, skipping topics when data is sparse rather than padding with meta-commentary like "no amenities are listed". This gives uniformity where it helps readers, and honesty where forcing uniformity would just invent filler.

---

### Flow 2: Service Descriptions (service categories)

This flow generates descriptions for **service categories** — not individual facilities, but the general service type a municipality offers (e.g., "Swimming hall services in Liminka").

In PTV, a "Service" is a higher-level concept that groups together one or more "Service Locations" (individual facilities). For example, the service "Swimming halls" might have two service locations: "Main swimming hall" and "Children's pool facility".

**What triggers it:** A user clicks "Generate description" for a service category in the PTV services view. Can also be run in batch for all service categories.

#### User Prompt

The service description prompt is simpler and more administrative:

> Based on the provided JSON structure, create two administrative descriptions of the sports service for the Service Information Repository:
> 1. Factual summary (maximum 150 characters)
> 2. Service description (2-4 paragraphs)
>
> Requirements: Present only verifiable facts. Use administrative language. Avoid marketing language. No greetings or welcoming phrases. No exclamation marks.

The same system instruction (v3) is used.

#### What Data Goes INTO the AI

A much simpler, aggregated overview document:

- **City name** — the municipality name in all languages (e.g., `{fi: "Raahe", se: "Brahestad", en: "Raahe"}`)
- **Service name** — the sub-category name in all languages (e.g., `{fi: "Pallokentät", se: "Bollplaner", en: "Ball games courts"}`)
- **List of facilities** — a simple list showing what types of facilities exist under this service in the municipality (just the type names, e.g., `[{type: "Pesäpallokenttä"}, {type: "Tenniskenttä"}]`)

This is intentionally minimal. The AI does not receive detailed facility data — only enough to understand what kind of service the municipality provides and how many facilities fall under it.

#### What Comes OUT of the AI

The same structured format as Flow 1: summary (max 150 chars) and description in three languages.

The description focuses on what the service type is about in general (e.g., what ball court services include, who they serve), rather than describing specific facilities.

#### Real Example: Input Data

For **"Ball games courts" in Raahe** (sub-category 1300, city-code 678), the AI receives:

```json
{
  "city-name": { "fi": "Raahe", "se": "Brahestad", "en": "Raahe" },
  "service-name": { "fi": "Pallokentät", "se": "Bollplaner", "en": "Ball games courts" },
  "sports-facilities": [
    { "type": "Pesäpallokenttä" },
    { "type": "Pesäpallokenttä" },
    { "type": "Pesäpallokenttä" },
    { "type": "Pesäpallokenttä" },
    { "type": "Tenniskenttä" },
    { "type": "Tenniskenttä" },
    "... (12 tennis courts total)",
    { "type": "Beachvolley-/rantalentopallokenttä" },
    { "type": "Beachvolley-/rantalentopallokenttä" },
    { "type": "Koripallokenttä" },
    { "type": "Pallokenttä" },
    "... (29 general ball fields total)"
  ]
}
```

#### Real Example: AI Output

**Summary:**

| Language | Text |
|---|---|
| fi | Raaheen on useita pesäpallo-, tennis-, beach volley- ja koripallokenttiä julkiseen käyttöön. |
| se | I Brahestad finns flera pesäpall-, tennis-, beachvolley- och basketplaner för allmän användning. |
| en | Raahe has multiple pesäpallo, tennis, beach volleyball, and basketball courts for public use. |

**Description:**

| Language | Text |
|---|---|
| fi | Raaheen on sijoitettu useita pallokenttiä, mukaan lukien pesäpallokenttiä, tenniskenttiä, beach volley -kenttiä ja koripallokenttiä. Kentät ovat julkisesti käytettävissä ja soveltuvat erilaisten pallopelien harrastamiseen. Käyttö on pääsääntöisesti maksutonta, mutta tietyissä tapauksissa voi olla varaus- tai käyttörajoituksia. Kenttien käyttöajat ja saatavuus vaihtelevat vuodenajan ja ylläpidon mukaan. |
| se | I Brahestad finns flera bollplaner, inklusive pesäpallplaner, tennisplaner, beachvolleyplaner och basketplaner. Planerna är tillgängliga för allmänheten och är avsedda för olika bollspel. Användningen är i allmänhet kostnadsfri, men vissa planer kan kräva bokning eller ha användningsbegränsningar. Planernas öppettider och tillgänglighet varierar beroende på säsong och underhåll. |
| en | Raahe has multiple ball game courts, including pesäpallo, tennis, beach volleyball, and basketball courts. The courts are publicly accessible and intended for various ball sports. Usage is generally free, but some courts may require reservations or have usage restrictions. Operating hours and availability vary according to season and maintenance. |

Notice how with the limited input, the AI:
- Enumerated the types of courts available (derived from counting the facility list)
- Wrote at a higher, more general level than the facility-specific descriptions
- Added reasonable general statements about public access and seasonal availability
- Stayed factual and administrative

---

### Flow 3: Translation

When a facility already has a description in one language, the AI can translate it to the other two. The prompt:

> Translate the following Service Information Repository descriptions from **[source language]** to **[target languages]**:
> 1. Administrative summary (maximum 150 characters)
> 2. Service description (2-3 paragraphs)
>
> Requirements: Maintain administrative tone in target languages. Keep the same information hierarchy. Preserve factual content without adding promotional elements. Use appropriate administrative language for each target language. Keep the same paragraph structure. Maintain character limits for summary.

The original source text is preserved exactly — only the target language fields are filled with AI output.

---

### Key Differences Between the Two Flows

| Aspect | Service Location (Flow 1) | Service (Flow 2) |
|---|---|---|
| **Describes** | One specific facility | A category of facilities |
| **Input richness** | Detailed (properties, contact, comments) | Minimal (city, category, list of types) |
| **Example** | "Pattasten naistenkenttä" | "Ball games courts in Raahe" |
| **PTV concept** | Palvelupaikka (Service Location) | Palvelu (Service) |
| **Typical count** | Hundreds per municipality | Tens per municipality |

### AI Model Parameters

**Primary: Google Gemini 3 Flash**

| Parameter | Value | Effect |
|---|---|---|
| Model | `gemini-3-flash-preview` | Fast, 1M input / 64K output tokens |
| top_p | 0.90 | Balanced sampling |
| temperature | 1.0 | Default sampling temperature |
| max_tokens | 8192 (single), 32768 (batch) | Upper bound for response length |
| thinking level | `"minimal"` | Fastest response path |
| Response format | `responseMimeType: "application/json"` + `responseSchema` | Model-enforced structured output |

**Fallback: OpenAI** — automatically used when Gemini returns 503/429 capacity errors. The batch flow does not use the OpenAI fallback because structured output with dynamic schemas is Gemini-specific in the current setup.

| Parameter | Value | Effect |
|---|---|---|
| Model | `gpt-4.1-mini` | Lightweight, fast, cost-efficient |
| top_p | 0.5 | Conservative word choices |
| presence_penalty | -2 | Encourages repetition of key terms |
| max_tokens | 4096 | Upper bound for response length |
| Response format | JSON Schema (strict mode) | Forces exact output structure |
