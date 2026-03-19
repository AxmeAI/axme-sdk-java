# axme-sdk-java

**Official Java SDK for the AXME platform.** Send and manage intents, poll lifecycle events and history, handle approvals, and access the enterprise admin surface — built for Java 11+, with clean exception types and request/response maps.

> **Alpha** · API surface is stabilizing. Not recommended for production workloads yet.  
> **Alpha** — install CLI, log in, run your first example in under 5 minutes. [Quick Start](https://cloud.axme.ai/alpha/cli) · [hello@axme.ai](mailto:hello@axme.ai)

---

## What Is AXME?

AXME is a coordination infrastructure for durable execution of long-running intents across distributed systems.

It provides a model for executing **intents** — requests that may take minutes, hours, or longer to complete — across services, agents, and human participants.

## AXP — the Intent Protocol

At the core of AXME is **AXP (Intent Protocol)** — an open protocol that defines contracts and lifecycle rules for intent processing.

AXP can be implemented independently.  
The open part of the platform includes:

- the protocol specification and schemas
- SDKs and CLI for integration
- conformance tests
- implementation and integration documentation

## AXME Cloud

**AXME Cloud** is the managed service that runs AXP in production together with **The Registry** (identity and routing).

It removes operational complexity by providing:

- reliable intent delivery and retries  
- lifecycle management for long-running operations  
- handling of timeouts, waits, reminders, and escalation  
- observability of intent status and execution history  

State and events can be accessed through:

- API and SDKs  
- event streams and webhooks  
- the cloud console

---

## What You Can Do With This SDK

- **Send intents** — create typed, durable actions with delivery guarantees
- **Poll lifecycle events** — retrieve real-time state events and intent history via `listIntentEvents`
- **Approve or reject** — handle human-in-the-loop steps from Java services
- **Control workflows** — pause, resume, cancel, update retry policies and reminders
- **Administer** — manage organizations, workspaces, service accounts, and grants

---

## Install

Build and install from source (local Maven repository):

```bash
git clone https://github.com/AxmeAI/axme-sdk-java.git
cd axme-sdk-java
mvn -q -DskipTests install
```

Then add to your `pom.xml`:

```xml
<dependency>
    <groupId>ai.axme</groupId>
    <artifactId>axme</artifactId>
    <version>0.1.2</version>
</dependency>
```

Maven Central publication target: `ai.axme:axme`.

---

## Quickstart

```java
import dev.axme.sdk.AxmeClient;
import dev.axme.sdk.AxmeClientConfig;
import dev.axme.sdk.RequestOptions;
import java.util.Map;

public class Quickstart {
    public static void main(String[] args) throws Exception {
        AxmeClient client = new AxmeClient(
            AxmeClientConfig.forCloud(
                "AXME_API_KEY", // sent as x-api-key
                "OPTIONAL_USER_OR_SESSION_TOKEN" // sent as Authorization: Bearer
            )
        );
        // Optional override for staging/dev:
        // AxmeClientConfig cfg = new AxmeClientConfig("https://staging-api.cloud.axme.ai", "AXME_API_KEY");

        // Check connectivity / discover available capabilities
        System.out.println(client.getCapabilities(RequestOptions.none()));

        // Send an intent to a registered agent address
        Map<String, Object> intent = client.createIntent(
            Map.of(
                "intent_type", "order.fulfillment.v1",
                "to_agent", "agent://acme-corp/production/fulfillment-service",
                "payload", Map.of("order_id", "ord_123", "priority", "high")
            ),
            new RequestOptions("fulfill-ord-123-001", null)
        );
        System.out.println(intent.get("intent_id") + " " + intent.get("status"));

        // List registered agent addresses
        Map<String, Object> agents = client.listAgents("acme-corp-uuid", "prod-ws-uuid", null, RequestOptions.none());
        System.out.println(agents.get("agents"));
    }
}
```

---

## Minimal Language-Native Example

Short basic submit/get example:

- [`examples/BasicSubmit.java`](examples/BasicSubmit.java)

Run (after SDK build/install):

```bash
export AXME_API_KEY="axme_sa_..."
# compile/run according to your app setup, reusing examples/BasicSubmit.java
```

Full runnable scenario set lives in:

- Cloud: <https://github.com/AxmeAI/axme-examples/tree/main/cloud>
- Protocol-only: <https://github.com/AxmeAI/axme-examples/tree/main/protocol>

---

## API Method Families

The SDK covers the full public API surface:

![API Method Family Map](https://raw.githubusercontent.com/AxmeAI/axme-docs/main/docs/diagrams/api/01-api-method-family-map.svg)

*D1 families (intents, inbox, approvals) are the core integration path. D2 adds schemas, webhooks, and media. D3 covers enterprise admin. The Java SDK implements all three tiers.*

---

## Pagination and Cursor Flows

Cursor-based list endpoints are available for inbox change streams:

![Pagination, Filtering, and Sorting Patterns](https://raw.githubusercontent.com/AxmeAI/axme-docs/main/docs/diagrams/api/03-pagination-filtering-sorting-patterns.svg)

*All list methods return a `cursor` field for the next page. Pass it as `after` in the next call. Filter and sort parameters are typed per endpoint.*

```java
// Paginate through inbox changes
Map<String, Object> page = client.listInboxChanges(
    "agent://manager",
    null,
    50,
    RequestOptions.none()
);

while (page.get("cursor") != null) {
    page = client.listInboxChanges(
        "agent://manager",
        (String) page.get("cursor"),
        50,
        RequestOptions.none()
    );
}
```

---

## Human-in-the-Loop (8 Task Types)

AXME supports 8 human task types. Each pauses the workflow and notifies a human via email with a link to a web task page.

| Task type | Use case | Default outcomes |
|-----------|----------|-----------------|
| `approval` | Approve or reject a request | approved, rejected |
| `confirmation` | Confirm a real-world action completed | confirmed, denied |
| `review` | Review content with multiple outcomes | approved, changes_requested, rejected |
| `assignment` | Assign work to a person or team | assigned, declined |
| `form` | Collect structured data via form fields | submitted |
| `clarification` | Request clarification (comment required) | provided, declined |
| `manual_action` | Physical task completion (evidence required) | completed, failed |
| `override` | Override a policy gate (comment required) | override_approved, rejected |

```java
// Create an intent with a human task step
var result = client.createIntent(CreateIntentParams.builder()
    .intentType("intent.budget.approval.v1")
    .toAgent("agent://agent_core")
    .payload(Map.of("amount", 32000, "department", "engineering"))
    .humanTask(HumanTask.builder()
        .title("Approve Q3 budget")
        .description("Review and approve the Q3 infrastructure budget.")
        .taskType("approval")
        .notifyEmail("approver@example.com")
        .allowedOutcomes(List.of("approved", "rejected"))
        .build())
    .build());
```

Task types with forms use `form_schema` to define required fields:

```java
HumanTask.builder()
    .title("Assign incident commander")
    .taskType("assignment")
    .notifyEmail("oncall@example.com")
    .formSchema(Map.of(
        "type", "object",
        "required", List.of("assignee"),
        "properties", Map.of(
            "assignee", Map.of("type", "string", "title", "Commander name"),
            "priority", Map.of("type", "string", "enum", List.of("P1", "P2", "P3"))
        )
    ))
    .build()
```

### Programmatic approvals (inbox API)

```java
Map<String, Object> inbox = client.listInbox("agent://manager", RequestOptions.none());

for (Object item : (List<?>) inbox.get("items")) {
    Map<?, ?> entry = (Map<?, ?>) item;
    Map<String, Object> action = (Map<String, Object>) entry.get("action");
    if (action == null || action.get("approval_id") == null) {
        continue;
    }
    client.decideApproval(
        (String) action.get("approval_id"),
        Map.of("decision", "approve", "reason", "Reviewed and approved"),
        RequestOptions.none()
    );
}
```

---

## Intents

```java
Map<String, Object> created = client.createIntent(
    Map.of(
        "intent_type", "order.fulfillment.v1",
        "owner_agent", "agent://fulfillment-service",
        "payload", Map.of("order_id", "ord_123")
    ),
    new RequestOptions("intent-ord-123", null)
);

String intentId = (String) created.get("intent_id");

Map<String, Object> current = client.getIntent(intentId, RequestOptions.none());
Map<String, Object> events = client.listIntentEvents(intentId, null, RequestOptions.none());
```

---

## Enterprise Admin APIs

The Java SDK includes the full service-account lifecycle surface:

```java
// Create a service account
Map<String, Object> sa = client.createServiceAccount(
    Map.of("name", "ci-runner", "org_id", "org_abc"),
    new RequestOptions("sa-ci-runner-001", null)
);

// Issue a key
Map<String, Object> key = client.createServiceAccountKey(
    (String) sa.get("id"),
    Map.of(),
    new RequestOptions(null, null)
);

// List service accounts
client.listServiceAccounts("org_abc", "", RequestOptions.none());

// Revoke a key
client.revokeServiceAccountKey(
    (String) sa.get("id"),
    (String) key.get("key_id"),
    RequestOptions.none()
);
```

Available methods:
- `createServiceAccount` / `listServiceAccounts` / `getServiceAccount`
- `createServiceAccountKey` / `revokeServiceAccountKey`

---

## Nick and Identity Registry

```java
Map<String, Object> registered = client.registerNick(
    Map.of("nick", "@partner.user", "display_name", "Partner User"),
    new RequestOptions("nick-register-001", null)
);

Map<String, Object> check = client.checkNick("@partner.user", RequestOptions.none());

Map<String, Object> renamed = client.renameNick(
    Map.of("owner_agent", registered.get("owner_agent"), "nick", "@partner.new"),
    new RequestOptions("nick-rename-001", null)
);

Map<String, Object> profile = client.getUserProfile(
    (String) registered.get("owner_agent"),
    RequestOptions.none()
);
```

---

## Repository Structure

```
axme-sdk-java/
├── src/
│   ├── main/java/dev/axme/sdk/
│   │   ├── AxmeClient.java        # All API methods
│   │   ├── AxmeClientConfig.java  # Configuration
│   │   ├── RequestOptions.java    # Idempotency key and correlation ID
│   │   └── AxmeAPIException.java  # Typed exception
│   └── test/                      # JUnit test suite
├── examples/
│   └── BasicSubmit.java           # Minimal language-native quickstart
└── docs/
```

---

## Tests

```bash
mvn test
```

---

## Related Repositories

| Repository | Role |
|---|---|
| [axme-docs](https://github.com/AxmeAI/axme-docs) | Full API reference and integration guides |
| [axme-spec](https://github.com/AxmeAI/axme-spec) | Schema contracts this SDK implements |
| [axme-conformance](https://github.com/AxmeAI/axme-conformance) | Conformance suite that validates this SDK |
| [axme-examples](https://github.com/AxmeAI/axme-examples) | Runnable examples using this SDK |
| [axme-sdk-python](https://github.com/AxmeAI/axme-sdk-python) | Python equivalent |
| [axme-sdk-typescript](https://github.com/AxmeAI/axme-sdk-typescript) | TypeScript equivalent |

---

## Contributing & Contact

- Bug reports and feature requests: open an issue in this repository
- Quick Start: https://cloud.axme.ai/alpha/cli · Contact: [hello@axme.ai](mailto:hello@axme.ai)
- Security disclosures: see [SECURITY.md](SECURITY.md)
- Contribution guidelines: [CONTRIBUTING.md](CONTRIBUTING.md)
