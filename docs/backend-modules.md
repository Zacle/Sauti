# Backend module boundaries

Sauti is intentionally a Spring Boot modular monolith. It deploys as one application, while feature packages remain the unit of ownership and future extraction.

| Java package | Responsibility |
| --- | --- |
| `com.sauti.api` | HTTP/WebSocket adapters, request validation, CORS, and OpenAPI. Controllers delegate to feature services and never import repositories. |
| `com.sauti.auth` | Accounts, password and Google sign-in, verification, password recovery, cookie sessions with refresh-token families, and request security (workspace routing and route permissions). Layered: `domain`, `application` (use cases + ports), `infrastructure`. |
| `com.sauti.tenant` | Workspaces: profile and settings, memberships, roles and permissions, invitations. New code is layered like `auth`; `Tenant` and the settings services at the root predate it. |
| `com.sauti.business` | Workspace business profile: name, contact details, typed services (price, duration), FAQs and policies, shared by all agents. Layered like `auth`; agents read it through the `agent.BusinessFacts` port. |
| `com.sauti.agent` | Agent identity, prompt, voice, languages, schedules, templates, and call behavior. Owns drafts and versions; other modules add their part of a version (tools, integrations, documents) through the `agent.AgentConfigurationPart` port. |
| `com.sauti.call` | Call lifecycle, browser/phone media runtimes, transcripts, recordings, interruption handling, and voice-runtime metrics. |
| `com.sauti.voice` | Provider-facing voice catalog and preview behavior. |
| `com.sauti.nlp` | Language detection and speech-processing helpers. |
| `com.sauti.llm` | LLM routing, prompt context, structured decisions, primary/fallback provider behavior. |
| `com.sauti.knowledge` | Agent knowledge sources, indexing, and retrieval. |
| `com.sauti.calendar` | Availability and booking lifecycle. |
| `com.sauti.integration` | Workspace provider connections, agent enablement, OAuth providers, and durable post-call actions. |
| `com.sauti.tool` | Explicit, policy-controlled during-call tools and encrypted credentials. |
| `com.sauti.analytics` | Tenant-scoped call aggregation and outcome metrics. |
| `com.sauti.webanalytics` | Privacy-preserving public acquisition events and platform-admin funnel reporting. |
| `com.sauti.billing` | Plans, limits, and usage. |
| `com.sauti.outbound` | Outbound call orchestration. |
| `com.sauti.telnyx` | Telnyx-specific call-control and media adapters. |
| `com.sauti.whatsapp` | WhatsApp webhooks and messaging. |
| `com.sauti.webhook` | Signed, retryable outbound webhook delivery. |
| `com.sauti.session` | Session-level state that does not belong to a provider adapter. |
| `com.sauti.dashboard` | Backend projections used by the dashboard overview. |
| `com.sauti.shared` | Cross-cutting infrastructure only: production safety, shared errors, and distributed rate limiting. |

## Enforced rules

- Controllers stay thin and cannot import `*Repository`; `ApiBoundaryTest` enforces this at build time.
- Customer-data repository access must be tenant-scoped or be immediately guarded by an owning feature service.
- Cross-feature calls go through a public service API. Entities and repositories are not a substitute for an application boundary.
- Provider details stay behind feature-owned interfaces. Domain services must not branch on vendor HTTP payloads.
- Credentials are encrypted at rest and never returned from APIs or written to logs.
- Public and authentication traffic uses the shared Redis-backed limiter. Identifiers are hashed and the increment/expiry operation is atomic.
- Operational metrics use bounded tags only. Never tag a meter with tenant IDs, call IDs, phone numbers, agent IDs, or exception messages.
- Production starts with the `production` profile. `ProductionSafetyValidator` fails startup when development secrets, fake providers, insecure origins, or disabled webhook verification are detected.

## Dashboard boundaries

The Next.js App Router is an adapter layer. Route `page.tsx` files should remain thin. Feature behavior lives under `dashboard/features/<feature>`, API clients under `dashboard/lib/api`, and shared transport types in `dashboard/types/api.ts`.

ESLint, type checking, and the production build all run in CI. Feature-specific styling should use CSS modules; global styles are reserved for true design tokens and application-wide primitives.

## Workspace requests

- Every workspace feature is called as `/api/v1/workspaces/{workspaceId}/...`. `WorkspacePathFilter` hands it to the existing feature route and `WorkspaceAccessFilter` checks membership, workspace status and the route permission from `WorkspaceRoutePolicy` before the controller runs. `AuthenticatedUser.tenantId()` is the workspace from the URL.
- Account and platform routes (`/auth`, `/me`, `/workspaces`, `/invitations`, `/public`, `/admin`, `/operator`, `/voices`) never take a workspace.
- `LayeredModuleArchitectureTest` enforces the layering of `auth`, `tenant`, `audit` and `business`. Add a module to it when it is restructured.
