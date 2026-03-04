package dev.axme.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AxmeClient {
  private final String baseUrl;
  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AxmeClient(AxmeClientConfig config) {
    this(config, HttpClient.newHttpClient());
  }

  public AxmeClient(AxmeClientConfig config, HttpClient httpClient) {
    this.baseUrl = config.getBaseUrl();
    this.apiKey = config.getApiKey();
    this.httpClient = httpClient;
  }

  public Map<String, Object> registerNick(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/users/register-nick", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> checkNick(String nick, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/users/check-nick", Map.of("nick", nick), null, normalizeOptions(options));
  }

  public Map<String, Object> renameNick(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/users/rename-nick", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getUserProfile(String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "GET",
        "/v1/users/profile",
        Map.of("owner_agent", ownerAgent),
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> updateUserProfile(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/users/profile/update", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> createServiceAccount(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/service-accounts", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listServiceAccounts(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    return requestJson("GET", "/v1/service-accounts", query, null, normalizeOptions(options));
  }

  public Map<String, Object> getServiceAccount(String serviceAccountId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/service-accounts/" + serviceAccountId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> createServiceAccountKey(String serviceAccountId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "POST",
        "/v1/service-accounts/" + serviceAccountId + "/keys",
        Map.of(),
        payload,
        normalizeOptions(options));
  }

  public Map<String, Object> revokeServiceAccountKey(String serviceAccountId, String keyId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "POST",
        "/v1/service-accounts/" + serviceAccountId + "/keys/" + keyId + "/revoke",
        Map.of(),
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> createIntent(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/intents", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getIntent(String intentId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/intents/" + intentId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> listIntentEvents(String intentId, Integer since, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (since != null && since >= 0) {
      query.put("since", Integer.toString(since));
    }
    return requestJson("GET", "/v1/intents/" + intentId + "/events", query, null, normalizeOptions(options));
  }

  public Map<String, Object> resolveIntent(String intentId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    RequestOptions normalized = normalizeOptions(options);
    return requestJson(
        "POST",
        "/v1/intents/" + intentId + "/resolve",
        buildIntentControlQuery(normalized),
        payload,
        normalized);
  }

  public Map<String, Object> resumeIntent(String intentId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    RequestOptions normalized = normalizeOptions(options);
    return requestJson(
        "POST",
        "/v1/intents/" + intentId + "/resume",
        buildIntentControlQuery(normalized),
        payload,
        normalized);
  }

  public Map<String, Object> updateIntentControls(String intentId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    RequestOptions normalized = normalizeOptions(options);
    return requestJson(
        "POST",
        "/v1/intents/" + intentId + "/controls",
        buildIntentControlQuery(normalized),
        payload,
        normalized);
  }

  public Map<String, Object> updateIntentPolicy(String intentId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    RequestOptions normalized = normalizeOptions(options);
    return requestJson(
        "POST",
        "/v1/intents/" + intentId + "/policy",
        buildIntentControlQuery(normalized),
        payload,
        normalized);
  }

  public Map<String, Object> createAccessRequest(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/access-requests", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listAccessRequests(String orgId, String workspaceId, String state, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(orgId)) {
      query.put("org_id", orgId);
    }
    if (!isBlank(workspaceId)) {
      query.put("workspace_id", workspaceId);
    }
    if (!isBlank(state)) {
      query.put("state", state);
    }
    return requestJson("GET", "/v1/access-requests", query, null, normalizeOptions(options));
  }

  public Map<String, Object> getAccessRequest(String accessRequestId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/access-requests/" + accessRequestId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> reviewAccessRequest(String accessRequestId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "POST",
        "/v1/access-requests/" + accessRequestId + "/review",
        Map.of(),
        payload,
        normalizeOptions(options));
  }

  public Map<String, Object> bindAlias(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/aliases", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listAliases(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    return requestJson(
        "GET",
        "/v1/aliases",
        query,
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> revokeAlias(String aliasId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/aliases/" + aliasId + "/revoke", Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> resolveAlias(String orgId, String workspaceId, String alias, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    query.put("alias", alias);
    return requestJson(
        "GET",
        "/v1/aliases/resolve",
        query,
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> decideApproval(String approvalId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "POST",
        "/v1/approvals/" + approvalId + "/decision",
        Map.of(),
        payload,
        normalizeOptions(options));
  }

  public Map<String, Object> getCapabilities(RequestOptions options) throws IOException, InterruptedException {
    return requestJson("GET", "/v1/capabilities", Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> listInbox(String ownerAgent, RequestOptions options) throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("GET", "/v1/inbox", query, null, normalizeOptions(options));
  }

  public Map<String, Object> getInboxThread(String threadId, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("GET", "/v1/inbox/" + threadId, query, null, normalizeOptions(options));
  }

  public Map<String, Object> listInboxChanges(String ownerAgent, String cursor, Integer limit, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    if (!isBlank(cursor)) {
      query.put("cursor", cursor);
    }
    if (limit != null && limit >= 0) {
      query.put("limit", Integer.toString(limit));
    }
    return requestJson("GET", "/v1/inbox/changes", query, null, normalizeOptions(options));
  }

  public Map<String, Object> replyInboxThread(String threadId, String message, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/inbox/" + threadId + "/reply", query, Map.of("message", message), normalizeOptions(options));
  }

  public Map<String, Object> delegateInboxThread(String threadId, Map<String, Object> payload, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/inbox/" + threadId + "/delegate", query, payload, normalizeOptions(options));
  }

  public Map<String, Object> approveInboxThread(String threadId, Map<String, Object> payload, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/inbox/" + threadId + "/approve", query, payload, normalizeOptions(options));
  }

  public Map<String, Object> rejectInboxThread(String threadId, Map<String, Object> payload, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/inbox/" + threadId + "/reject", query, payload, normalizeOptions(options));
  }

  public Map<String, Object> deleteInboxMessages(String threadId, Map<String, Object> payload, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/inbox/" + threadId + "/messages/delete", query, payload, normalizeOptions(options));
  }

  public Map<String, Object> createInvite(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/invites/create", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getInvite(String token, RequestOptions options) throws IOException, InterruptedException {
    return requestJson("GET", "/v1/invites/" + token, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> acceptInvite(String token, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/invites/" + token + "/accept", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> createMediaUpload(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/media/create-upload", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getMediaUpload(String uploadId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/media/" + uploadId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> finalizeMediaUpload(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/media/finalize-upload", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> upsertSchema(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/schemas", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getSchema(String semanticType, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/schemas/" + semanticType, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> upsertWebhookSubscription(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/webhooks/subscriptions", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listWebhookSubscriptions(String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("GET", "/v1/webhooks/subscriptions", query, null, normalizeOptions(options));
  }

  public Map<String, Object> deleteWebhookSubscription(String subscriptionId, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("DELETE", "/v1/webhooks/subscriptions/" + subscriptionId, query, null, normalizeOptions(options));
  }

  public Map<String, Object> publishWebhookEvent(Map<String, Object> payload, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/webhooks/events", query, payload, normalizeOptions(options));
  }

  public Map<String, Object> replayWebhookEvent(String eventId, String ownerAgent, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(ownerAgent)) {
      query.put("owner_agent", ownerAgent);
    }
    return requestJson("POST", "/v1/webhooks/events/" + eventId + "/replay", query, null, normalizeOptions(options));
  }

  public Map<String, Object> createOrganization(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/organizations", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getOrganization(String orgId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/organizations/" + orgId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> updateOrganization(String orgId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("PATCH", "/v1/organizations/" + orgId, Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> createWorkspace(String orgId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/organizations/" + orgId + "/workspaces", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listWorkspaces(String orgId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/organizations/" + orgId + "/workspaces", Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> updateWorkspace(String orgId, String workspaceId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "PATCH",
        "/v1/organizations/" + orgId + "/workspaces/" + workspaceId,
        Map.of(),
        payload,
        normalizeOptions(options));
  }

  public Map<String, Object> listOrganizationMembers(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(workspaceId)) {
      query.put("workspace_id", workspaceId);
    }
    return requestJson("GET", "/v1/organizations/" + orgId + "/members", query, null, normalizeOptions(options));
  }

  public Map<String, Object> addOrganizationMember(String orgId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/organizations/" + orgId + "/members", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> updateOrganizationMember(String orgId, String memberId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson(
        "PATCH",
        "/v1/organizations/" + orgId + "/members/" + memberId,
        Map.of(),
        payload,
        normalizeOptions(options));
  }

  public Map<String, Object> removeOrganizationMember(String orgId, String memberId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("DELETE", "/v1/organizations/" + orgId + "/members/" + memberId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> updateQuota(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("PATCH", "/v1/quotas", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getQuota(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    return requestJson(
        "GET",
        "/v1/quotas",
        query,
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> getUsageSummary(String orgId, String workspaceId, String window, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    if (!isBlank(window)) {
      query.put("window", window);
    }
    return requestJson("GET", "/v1/usage/summary", query, null, normalizeOptions(options));
  }

  public Map<String, Object> getUsageTimeseries(String orgId, String workspaceId, Integer windowDays, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    if (windowDays != null && windowDays >= 0) {
      query.put("window_days", Integer.toString(windowDays));
    }
    return requestJson("GET", "/v1/usage/timeseries", query, null, normalizeOptions(options));
  }

  public Map<String, Object> createPrincipal(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/principals", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getPrincipal(String principalId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/principals/" + principalId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> registerRoutingEndpoint(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/routing/endpoints", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listRoutingEndpoints(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    return requestJson(
        "GET",
        "/v1/routing/endpoints",
        query,
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> updateRoutingEndpoint(String routeId, Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("PATCH", "/v1/routing/endpoints/" + routeId, Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> removeRoutingEndpoint(String routeId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("DELETE", "/v1/routing/endpoints/" + routeId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> resolveRouting(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/routing/resolve", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> upsertTransportBinding(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/transports/bindings", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listTransportBindings(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    return requestJson(
        "GET",
        "/v1/transports/bindings",
        query,
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> removeTransportBinding(String bindingId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("DELETE", "/v1/transports/bindings/" + bindingId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> submitDelivery(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/deliveries", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> listDeliveries(String orgId, String workspaceId, String principalId, String status, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    if (!isBlank(principalId)) {
      query.put("principal_id", principalId);
    }
    if (!isBlank(status)) {
      query.put("status", status);
    }
    return requestJson("GET", "/v1/deliveries", query, null, normalizeOptions(options));
  }

  public Map<String, Object> getDelivery(String deliveryId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/deliveries/" + deliveryId, Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> replayDelivery(String deliveryId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("POST", "/v1/deliveries/" + deliveryId + "/replay", Map.of(), null, normalizeOptions(options));
  }

  public Map<String, Object> updateBillingPlan(Map<String, Object> payload, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("PATCH", "/v1/billing/plan", Map.of(), payload, normalizeOptions(options));
  }

  public Map<String, Object> getBillingPlan(String orgId, String workspaceId, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    return requestJson(
        "GET",
        "/v1/billing/plan",
        query,
        null,
        normalizeOptions(options));
  }

  public Map<String, Object> listBillingInvoices(String orgId, String workspaceId, String billingStatus, RequestOptions options)
      throws IOException, InterruptedException {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("org_id", orgId);
    query.put("workspace_id", workspaceId);
    if (!isBlank(billingStatus)) {
      query.put("status", billingStatus);
    }
    return requestJson("GET", "/v1/billing/invoices", query, null, normalizeOptions(options));
  }

  public Map<String, Object> getBillingInvoice(String invoiceId, RequestOptions options)
      throws IOException, InterruptedException {
    return requestJson("GET", "/v1/billing/invoices/" + invoiceId, Map.of(), null, normalizeOptions(options));
  }

  private Map<String, Object> requestJson(
      String method,
      String path,
      Map<String, String> query,
      Map<String, Object> payload,
      RequestOptions options)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(buildUrl(path, query)))
            .method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .header("Accept", "application/json");

    String resolvedAuthorization = isBlank(options.getAuthorization()) ? "Bearer " + apiKey : options.getAuthorization();
    builder.header("Authorization", resolvedAuthorization);

    if (payload != null) {
      builder.header("Content-Type", "application/json");
    }
    if (!isBlank(options.getIdempotencyKey())) {
      builder.header("Idempotency-Key", options.getIdempotencyKey());
    }
    if (!isBlank(options.getTraceId())) {
      builder.header("X-Trace-Id", options.getTraceId());
    }
    if (!isBlank(options.getXOwnerAgent())) {
      builder.header("x-owner-agent", options.getXOwnerAgent());
    }

    HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new AxmeHttpException(response.statusCode(), response.body());
    }
    if (response.body() == null || response.body().trim().isEmpty()) {
      return Map.of();
    }

    return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
  }

  private static Map<String, String> buildIntentControlQuery(RequestOptions options) {
    Map<String, String> query = new LinkedHashMap<>();
    if (!isBlank(options.getOwnerAgent())) {
      query.put("owner_agent", options.getOwnerAgent());
    }
    return query;
  }

  private String buildUrl(String path, Map<String, String> query) {
    if (query == null || query.isEmpty()) {
      return baseUrl + path;
    }

    Map<String, String> filtered = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : query.entrySet()) {
      if (!isBlank(entry.getValue())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }
    if (filtered.isEmpty()) {
      return baseUrl + path;
    }

    StringBuilder builder = new StringBuilder(baseUrl).append(path).append("?");
    boolean first = true;
    for (Map.Entry<String, String> entry : filtered.entrySet()) {
      if (!first) {
        builder.append("&");
      }
      first = false;
      builder
          .append(urlEncode(entry.getKey()))
          .append("=")
          .append(urlEncode(entry.getValue()));
    }
    return builder.toString();
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static RequestOptions normalizeOptions(RequestOptions options) {
    return options == null ? RequestOptions.none() : options;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
