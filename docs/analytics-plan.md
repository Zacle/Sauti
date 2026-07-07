# Sauti Analytics Plan

## Context

The backend has a working `AnalyticsService` with 4 endpoints (summary, daily volume,
language breakdown, agent summary). The dashboard analytics page is a placeholder.
OmniDimension's analytics screen is the competitive benchmark.

This plan covers what to build on top of the existing foundation to match and exceed
that benchmark, with additions specific to the African market (language, channel, SMS).

---

## What OmniDimension Has (from screenshot)

| Section | What it shows |
|---|---|
| Date range filter | Last 7 / 30 / 90 days + custom range |
| Agent filter | All assistants or a single one |
| Summary KPIs | Total calls, Connect rate %, Total duration, Avg duration |
| Best time to call | Heatmap: day-of-week × time-of-day, grouped by connection rate |
| Outcomes over time | Stacked bar: completed / voicemail / no-answer / busy / failed — clickable |
| Connect rate over time | Line chart with a target % reference line |
| Call funnel | Horizontal bars: Attempted → Connected → Completed |
| By assistant | Per-agent volume + connect rate list, sortable |
| Tab: Website Chatbot Analytics | Separate tab for web channel |

---

## Sauti Analytics Plan

### Tier 1 — Core (matches OmniDimension baseline)

These use data already in the `calls` table. Most backend queries already exist or
need minor additions to `AnalyticsService`.

#### 1.1 Summary KPI Bar

| Metric | Source | Notes |
|---|---|---|
| Total calls | `COUNT(*)` | Already in `summary()` |
| Connect rate | `completed / attempted * 100` | New: needs `attempted` count |
| Total duration | `SUM(duration_seconds)` | New field |
| Avg duration | Already in `averageDurationSeconds` | ✅ exists |
| Bookings made | `bookingCalls` | ✅ exists |
| Transfers | `transferredCalls` | ✅ exists |

Each KPI shows a delta vs. the previous equal-length period ("↑12% vs last week").
This requires running the same query for `[from - range, from)` and comparing.

#### 1.2 Outcomes Over Time

Stacked bar chart, one bar per day, broken down by outcome:
`completed`, `transferred`, `voicemail`, `no_answer`, `busy`, `failed`, `after_hours`

Clicking a bar filters the call log to that day.

**Backend:** New endpoint `GET /api/v1/analytics/outcomes-by-day` — returns
`[{ date, completed, transferred, voicemail, no_answer, busy, failed, after_hours }]`.

#### 1.3 Call Funnel

Three-step horizontal funnel:
1. **Attempted** — all calls started
2. **Connected** — calls that reached the agent pipeline (not `failed` at connect)
3. **Completed** — calls that ended with a conversational outcome

**Backend:** Derived from the existing `summary()` fields + a new `attempted` count.

#### 1.4 Connect Rate Over Time

Line chart (connects / attempts per day) with a configurable target line (default 70%).

**Backend:** `GET /api/v1/analytics/connect-rate-by-day` — returns
`[{ date, rate, attempts }]`.

#### 1.5 By Agent Table

Already partially covered by `agentSummary()`. Needs two additions:
- Connect rate per agent
- Bookings per agent

Sortable columns: Calls · Connect rate · Avg duration · Bookings.

#### 1.6 Date Range + Agent Filter

- Quick picks: Last 7 days / 30 days / 90 days + custom date picker
- Agent selector: All agents or a single one (already a query param pattern)

---

### Tier 2 — Sauti Differentiators (beyond OmniDimension)

These use data Sauti already stores that OmniDimension does not show.

#### 2.1 Language Breakdown

Pie or bar chart: call volume by detected language (`sw`, `fr`, `en`, `ln`, `ar`, etc.).
`languageBreakdown()` already returns this — just needs a frontend chart.

Key insight: which language drives the most conversational turns vs. drop-offs.
This is a direct competitive differentiator for African businesses.

#### 2.2 Channel Breakdown

With Phase 1–2 of the channel plan live, calls come from `twilio`, `signalwire`,
`telnyx`, `webrtc`, and `whatsapp`. Show volume and outcome split by channel.

**Backend:** Add `direction` column to `outcomes-by-day` or a new `by-channel` endpoint.

#### 2.3 Sentiment Trend

Line chart: average sentiment score over time (positive / neutral / negative).
`sentiment` is already stored on every analysed call.

Actionable insight: if sentiment dips after a system prompt change, the business owner
can correlate it and revert.

#### 2.4 Top Intents

Horizontal bar chart: top 8–10 most common caller intents (from the `intent` field).
Helps businesses understand what callers actually want vs. what the agent was configured for.

**Backend:** `GET /api/v1/analytics/top-intents` — `GROUP BY intent ORDER BY COUNT DESC LIMIT 10`.

#### 2.5 After-Hours Volume

How many calls arrive outside business hours and what happens to them
(`take_message` vs `closed` behavior). Useful for businesses deciding whether to extend hours.

Already derivable from `after_hours = true` on the `calls` table.

#### 2.6 SMS / Integration Events

For agents with Telnyx SMS or WhatsApp enabled:
- How many calls triggered an SMS confirmation
- How many triggered a WhatsApp follow-up
- M-Pesa: payment requests attempted vs. completed

Requires joining `integration_deliveries` with `calls`.

#### 2.7 STT / LLM / TTS Latency Panel

`avgSttLatencyMs`, `avgLlmLatencyMs`, `avgTtsLatencyMs` are already returned by
`summary()` but not shown in the UI. This is a technical panel, hidden behind an
"Advanced" toggle — relevant for diagnosing quality issues, not for every business owner.

---

### Tier 3 — Future (requires new data collection)

| Feature | What it needs |
|---|---|
| Best time to call heatmap | Store call start time bucketed by hour-of-week. New `call_hourly_stats` materialized view or pre-aggregation. |
| Keyword / topic cloud | Run topic extraction on transcript at analysis time. Store top keywords per call. |
| Conversion funnel per agent template | Tag calls by template at creation time. |
| Scheduled email report | Weekly PDF/email digest to the business owner (Sunday 8am). Uses `PostCallIntegrationService` email channel. |
| Benchmark comparison | Compare this tenant's connect rate vs. anonymised industry average. Requires cross-tenant aggregation. |

---

## Dashboard UI Layout

```
/console/analytics
│
├── [Filter bar]  Last 7d · 30d · 90d · Custom   [Agent ▾]   [Channel ▾]
│
├── [KPI row]
│   Total calls · Connect rate · Total duration · Avg duration · Bookings · Transfers
│   (each with Δ vs prior period)
│
├── [Tabs]  Overview · Outcomes · Latency
│
├── Overview tab
│   ├── Call Funnel           (right ½)
│   ├── Outcomes over time    (full width bar chart, clickable days)
│   ├── Connect rate over time (line chart with target line)
│   ├── Language breakdown    (pie chart)
│   ├── Channel breakdown     (stacked bar or donut)
│   ├── Sentiment trend       (line chart)
│   ├── Top intents           (horizontal bar)
│   └── By agent              (sortable table)
│
├── Outcomes tab
│   ├── After-hours volume
│   └── Integration events (SMS / WhatsApp / M-Pesa)
│
└── Latency tab  [hidden by default, toggle "Developer view"]
    └── STT / LLM / TTS avg latency over time
```

---

## Backend Changes Needed

| Endpoint | Status | New work |
|---|---|---|
| `GET /summary` | ✅ exists | Add `attempted`, `totalDurationSeconds`, per-period delta |
| `GET /daily` | ✅ exists | Rename/extend to include outcome breakdown per day |
| `GET /by-language` | ✅ exists | No change |
| `GET /by-agent` | ✅ exists | Add `connectRate`, `bookings` per agent |
| `GET /outcomes-by-day` | ❌ new | Outcome columns per day |
| `GET /connect-rate-by-day` | ❌ new | Rate + attempts per day |
| `GET /by-channel` | ❌ new | Volume + outcomes grouped by `direction` |
| `GET /top-intents` | ❌ new | Top 10 intents by frequency |
| `GET /sentiment-by-day` | ❌ new | Avg sentiment score per day |
| `GET /after-hours` | ❌ new | After-hours volume + behavior split |
| `GET /integration-events` | ❌ new | SMS/WhatsApp/M-Pesa delivery counts |

All new endpoints follow the same pattern: tenant-scoped, optional `from`/`to`/`agentId`
query params, return `List<{date, ...metrics}>`.

---

## Frontend Dependencies

- Chart library: **Recharts** (already used in the dashboard based on Next.js setup)
  or **Tremor** (higher-level, fewer lines of code, fits the console aesthetic)
- Date range picker: shadcn/ui `DateRangePicker` or a lightweight alternative
- KPI delta badges: inline component, green ↑ / red ↓

---

## Implementation Order

```
Week 1:  KPI bar with deltas + Outcomes over time + Call funnel
Week 2:  Connect rate over time + By agent table + Language breakdown
Week 3:  Channel breakdown + Sentiment trend + Top intents
Week 4:  After-hours + Integration events + Latency panel
Later:   Best time to call heatmap (requires new data collection)
         Scheduled email report
```
