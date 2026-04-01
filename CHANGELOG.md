# Changelog

## [v0.2.0] - 2026-04-01

### Added
- **Agent Mesh module** - MeshClient with heartbeat, startHeartbeat, stopHeartbeat, reportMetric, listAgents, getAgent, kill, resume, listEvents
- Mesh module wired via `client.getMesh()` accessor (thread-safe lazy init)
- Dashboard URL: mesh.axme.ai

## 0.1.2 (2026-03-18)

### Features
- `sendIntent()` — convenience wrapper with auto-generated correlation_id
- `applyScenario()` — compile and submit scenario bundle
- `validateScenario()` — dry-run scenario validation
- `health()` — gateway health check
- `mcpInitialize()` — MCP protocol handshake
- `mcpListTools()` — list available MCP tools
- `mcpCallTool()` — invoke MCP tool

## 0.1.1 (2026-03-13)

- Initial alpha release with full AXME API coverage (84 methods)
- Intent lifecycle, inbox, webhooks, admin APIs
- Jackson JSON serialization, Java 11+ compatible
