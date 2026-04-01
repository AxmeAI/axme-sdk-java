# axme-sdk-java

**Java SDK for AXME** - send intents, poll for deliveries, resume workflows. Java 11+, clean exception hierarchy, no third-party HTTP dependencies.

[![Alpha](https://img.shields.io/badge/status-alpha-orange)](https://cloud.axme.ai/alpha/cli) [![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

**[Quick Start](https://cloud.axme.ai/alpha/cli)** · **[Docs](https://github.com/AxmeAI/axme-docs)** · **[Examples](https://github.com/AxmeAI/axme-examples)**

---

## Install

```xml
<dependency>
    <groupId>ai.axme</groupId>
    <artifactId>axme</artifactId>
    <version>0.1.2</version>
</dependency>
```

Maven Central publication is in progress. Build locally until then:

```bash
git clone https://github.com/AxmeAI/axme-sdk-java.git
cd axme-sdk-java && mvn -q -DskipTests install
```

---

## Quick Start

```java
import dev.axme.sdk.AxmeClient;
import dev.axme.sdk.AxmeClientConfig;
import dev.axme.sdk.RequestOptions;
import java.util.Map;

AxmeClient client = new AxmeClient(
    AxmeClientConfig.forCloud("axme_sa_...", null)
);

// Send an intent - survives crashes, retries, timeouts
Map<String, Object> intent = client.createIntent(
    Map.of(
        "intent_type", "order.fulfillment.v1",
        "to_agent",    "agent://myorg/production/fulfillment-service",
        "payload",     Map.of("order_id", "ord_123")
    ),
    new RequestOptions("fulfill-ord-123-001", null)
);
System.out.println(intent.get("intent_id") + " " + intent.get("status"));
```

---

## Human Approvals

```java
var result = client.createIntent(Map.of(
    "intent_type", "intent.budget.approval.v1",
    "to_agent",    "agent://myorg/prod/agent_core",
    "payload",     Map.of("amount", 32000),
    "human_task",  Map.of(
        "task_type",        "approval",
        "notify_email",     "approver@example.com",
        "allowed_outcomes", List.of("approved", "rejected")
    )
), new RequestOptions(null, null));
```

8 task types: `approval`, `confirmation`, `review`, `assignment`, `form`, `clarification`, `manual_action`, `override`. Full reference: [axme-docs](https://github.com/AxmeAI/axme-docs).

---

## Observe Lifecycle Events

```java
Map<String, Object> events = client.listIntentEvents(intentId, null, RequestOptions.none());
```

---

## Agent Mesh - Monitor and Govern

Agent Mesh gives every agent real-time health monitoring, policy enforcement, and a kill switch - all from a single dashboard.

```java
client.mesh().startHeartbeat();
client.mesh().reportMetric(Metric.builder().success(true).latencyMs(230).costUsd(0.02).build());
```

Set action policies (allowlist/denylist intent types) and cost policies (intents/day, $/day limits) per agent via dashboard or API. Mesh module coming soon to this SDK - [Python SDK](https://github.com/AxmeAI/axme-sdk-python) available now. [Full overview](https://github.com/AxmeAI/axme#agent-mesh---see-and-control-your-agents).

Open the live dashboard at [mesh.axme.ai](https://mesh.axme.ai) or run `axme mesh dashboard` from the CLI.

---

## Examples

See [`examples/BasicSubmit.java`](examples/BasicSubmit.java). More: [axme-examples](https://github.com/AxmeAI/axme-examples)

---

## Development

```bash
mvn test
```

---

## Related

| | |
|---|---|
| [axme-docs](https://github.com/AxmeAI/axme-docs) | API reference and integration guides |
| [axme-examples](https://github.com/AxmeAI/axme-examples) | Runnable examples |
| [axp-spec](https://github.com/AxmeAI/axp-spec) | Protocol specification |
| [axme-cli](https://github.com/AxmeAI/axme-cli) | CLI tool |
| [axme-conformance](https://github.com/AxmeAI/axme-conformance) | Conformance suite |

---

[hello@axme.ai](mailto:hello@axme.ai) · [Security](SECURITY.md) · [License](LICENSE)
