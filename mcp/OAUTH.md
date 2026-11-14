# Shared remote MCP OAuth engine

`McpOAuth` implements public-client authorization code flow for the MCP authorization specification
dated 2025-11-25. Hosts inject an HTTP client, browser-session adapter, PKCE crypto and credential
store. This module change does not enable a new app setting or supply platform browser/storage
adapters.

The engine discovers protected-resource and authorization-server metadata, validates their exact
resource/issuer identity, and requires PKCE S256. Clients can use dynamic registration or an explicit
pre-registered client ID. Dynamically registered clients retain refresh tokens only when the
registration response permits the refresh grant; omitted grants default to authorization code.
A pre-registered client ID must be configured by the host for the grants it intends to use.
Each browser session supplies its redirect before registration; redirects
must use HTTPS or HTTP loopback. State, redirect and issuer are checked before code exchange.

This initial policy requires RFC 9207 issuer-response support and a matching callback `iss`.
Servers without that defense are rejected before registration/browser launch. The first advertised
authorization server is selected. Client ID metadata documents and automatic scope escalation are
outside this implementation. A server without dynamic registration needs a pre-registered client ID.

Credentials are bound to their resource endpoint. Concurrent access/refresh is serialized. Before
refresh, the stored credential is removed because a rotating refresh token may be consumed even
when its response is lost. A successful replacement is stored before returning, including when the
caller cancels during persistence. An uncertain refresh requires sign-in instead of reusing the
potentially consumed token. Credential-store writes/removals must be durable when they return.

The optional Ktor access plugin checks the destination, adds the current Bearer token, and marks
only the rejected token expired after a 401. It throws without replaying that operation. A 403 requires
explicit reconnection for additional permissions. HTTP redirects are disabled on the engine's
configured client. Hosts using the access plugin must also disable redirects on their transport
client and bound HTTP requests with timeouts. Host HTTP engines must separately disable
connection-failure retries. Those platform engine settings and MCP transport integration belong in the follow-up change.

Each discovery sequence, registration and token request is bounded to 30 seconds. Authorization
times out after five minutes of waiting for the browser. Browser sessions are closed
on success, cancellation and failure. Pending state is process-local; process death abandons it.
Authorization attempts are serialized so only one browser session is active. Hosts must cancel
and await a pending authorization before disconnecting its credential ID. Disconnect removes
local credentials and does not revoke authorization-server consent.

JVM mock-endpoint tests cover discovery fallback and identity checks, public registration, PKCE
parameters, callback attacks, cancellation/timeouts, resource isolation, rotating refresh,
concurrent access, rejection handling and sanitized protocol errors. These tests validate the
shared engine and plugin, not platform browser routing, secure-storage implementations, HTTP-engine
retry behavior, natural assistant selection or general interoperability with every OAuth server.
