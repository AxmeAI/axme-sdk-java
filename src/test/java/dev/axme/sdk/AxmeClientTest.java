package dev.axme.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AxmeClientTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockWebServer server;
  private AxmeClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new AxmeClient(new AxmeClientConfig(server.url("/").toString(), "token"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void registerNickSendsPayloadAndHeaders() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"owner_agent\":\"agent://user/1\"}"));

    Map<String, Object> response =
        client.registerNick(
            Map.of("nick", "@partner.user", "display_name", "Partner User"),
            new RequestOptions("register-1", null));

    RecordedRequest request = server.takeRequest();
    assertEquals("POST", request.getMethod());
    assertEquals("/v1/users/register-nick", request.getPath());
    assertEquals("token", request.getHeader("x-api-key"));
    assertEquals("register-1", request.getHeader("Idempotency-Key"));

    Map<String, Object> body =
        objectMapper.readValue(request.getBody().readUtf8(), new TypeReference<Map<String, Object>>() {});
    assertEquals("@partner.user", body.get("nick"));
    assertTrue((Boolean) response.get("ok"));
  }

  @Test
  void clientSendsConfiguredActorToken() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"available\":true}"));

    AxmeClient actorClient =
        new AxmeClient(new AxmeClientConfig(server.url("/").toString(), "platform-token", "actor-token"));
    actorClient.checkNick("@partner.user", RequestOptions.none());

    RecordedRequest request = server.takeRequest();
    assertEquals("platform-token", request.getHeader("x-api-key"));
    assertEquals("Bearer actor-token", request.getHeader("Authorization"));
  }

  @Test
  void configRejectsConflictingActorTokenAliases() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AxmeClientConfig(
                "https://api.axme.test",
                "platform-token",
                "actor-a",
                "actor-b"));
  }

  @Test
  void configUsesDefaultBaseUrlWhenBlank() {
    AxmeClientConfig config = new AxmeClientConfig("", "platform-token");
    assertEquals("https://api.cloud.axme.ai", config.getBaseUrl());
  }

  @Test
  void checkNickSendsQueryParameter() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                "{\"ok\":true,\"nick\":\"@partner.user\",\"normalized_nick\":\"partner.user\",\"public_address\":\"partner.user@ax\",\"available\":true}"));

    Map<String, Object> response = client.checkNick("@partner.user", RequestOptions.none());

    RecordedRequest request = server.takeRequest();
    assertEquals("GET", request.getMethod());
    assertEquals("/v1/users/check-nick?nick=%40partner.user", request.getPath());
    assertTrue((Boolean) response.get("available"));
  }

  @Test
  void renameNickSendsPayloadAndIdempotency() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"nick\":\"@partner.new\"}"));

    Map<String, Object> response =
        client.renameNick(
            Map.of("owner_agent", "agent://user/1", "nick", "@partner.new"),
            new RequestOptions("rename-1", null));

    RecordedRequest request = server.takeRequest();
    assertEquals("POST", request.getMethod());
    assertEquals("/v1/users/rename-nick", request.getPath());
    assertEquals("rename-1", request.getHeader("Idempotency-Key"));
    assertEquals("@partner.new", response.get("nick"));
  }

  @Test
  void getUserProfileSendsOwnerAgentQuery() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"owner_agent\":\"agent://user/1\"}"));

    Map<String, Object> response = client.getUserProfile("agent://user/1", RequestOptions.none());

    RecordedRequest request = server.takeRequest();
    assertEquals("GET", request.getMethod());
    assertEquals("/v1/users/profile?owner_agent=agent%3A%2F%2Fuser%2F1", request.getPath());
    assertEquals("agent://user/1", response.get("owner_agent"));
  }

  @Test
  void updateUserProfileSendsPayloadAndIdempotency() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"display_name\":\"Partner User Updated\"}"));

    Map<String, Object> response =
        client.updateUserProfile(
            Map.of("owner_agent", "agent://user/1", "display_name", "Partner User Updated"),
            new RequestOptions("profile-1", null));

    RecordedRequest request = server.takeRequest();
    assertEquals("POST", request.getMethod());
    assertEquals("/v1/users/profile/update", request.getPath());
    assertEquals("profile-1", request.getHeader("Idempotency-Key"));
    assertEquals("Partner User Updated", response.get("display_name"));
  }

  @Test
  void serviceAccountLifecycleEndpointsAreReachable() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"service_account\":{\"service_account_id\":\"sa_123\"}}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"service_accounts\":[{\"service_account_id\":\"sa_123\"}]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"service_account\":{\"service_account_id\":\"sa_123\"}}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"key\":{\"key_id\":\"sak_123\",\"status\":\"active\"}}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"key\":{\"key_id\":\"sak_123\",\"status\":\"revoked\"}}"));

    client.createServiceAccount(
        Map.of(
            "org_id", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "workspace_id", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            "name", "sdk-runner",
            "created_by_actor_id", "actor_java"),
        new RequestOptions("sa-create-1", null));
    client.listServiceAccounts(
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        RequestOptions.none());
    client.getServiceAccount("sa_123", RequestOptions.none());
    client.createServiceAccountKey("sa_123", Map.of("created_by_actor_id", "actor_java"), RequestOptions.none());
    client.revokeServiceAccountKey("sa_123", "sak_123", RequestOptions.none());

    assertEquals("/v1/service-accounts", server.takeRequest().getPath());
    assertEquals(
        "/v1/service-accounts?org_id=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa&workspace_id=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        server.takeRequest().getPath());
    assertEquals("/v1/service-accounts/sa_123", server.takeRequest().getPath());
    assertEquals("/v1/service-accounts/sa_123/keys", server.takeRequest().getPath());
    assertEquals("/v1/service-accounts/sa_123/keys/sak_123/revoke", server.takeRequest().getPath());
  }

  @Test
  void intentLifecycleAndControlEndpointsAreReachable() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"intent_id\":\"it_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"intent\":{\"intent_id\":\"it_123\"}}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"events\":[]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"applied\":false,\"policy_generation\":4}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"applied\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"applied\":true,\"policy_generation\":5}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"applied\":true,\"policy_generation\":6}"));

    client.createIntent(
        Map.of(
            "intent_type", "notify.message.v1",
            "from_agent", "agent://self",
            "to_agent", "agent://target",
            "payload", Map.of("text", "hello")),
        RequestOptions.none());
    assertEquals("/v1/intents", server.takeRequest().getPath());

    client.getIntent("it_123", RequestOptions.none());
    assertEquals("/v1/intents/it_123", server.takeRequest().getPath());

    client.listIntentEvents("it_123", 2, RequestOptions.none());
    assertEquals("/v1/intents/it_123/events?since=2", server.takeRequest().getPath());

    client.resolveIntent(
        "it_123",
        Map.of("status", "COMPLETED", "expected_policy_generation", 3),
        new RequestOptions(null, "trace-1", "agent://owner", "agent://owner", "Bearer scoped-token"));
    RecordedRequest resolveRequest = server.takeRequest();
    assertEquals("/v1/intents/it_123/resolve?owner_agent=agent%3A%2F%2Fowner", resolveRequest.getPath());
    assertEquals("Bearer scoped-token", resolveRequest.getHeader("Authorization"));
    assertEquals("agent://owner", resolveRequest.getHeader("x-owner-agent"));
    assertEquals("trace-1", resolveRequest.getHeader("X-Trace-Id"));

    client.resumeIntent(
        "it_123",
        Map.of("approve_current_step", true, "expected_policy_generation", 2),
        new RequestOptions("resume-1", null, "agent://owner", null, null));
    RecordedRequest resumeRequest = server.takeRequest();
    assertEquals("/v1/intents/it_123/resume?owner_agent=agent%3A%2F%2Fowner", resumeRequest.getPath());
    assertEquals("resume-1", resumeRequest.getHeader("Idempotency-Key"));

    client.updateIntentControls(
        "it_123",
        Map.of("controls_patch", Map.of("timeout_seconds", 120), "expected_policy_generation", 5),
        RequestOptions.none());
    RecordedRequest controlsRequest = server.takeRequest();
    assertEquals("/v1/intents/it_123/controls", controlsRequest.getPath());
    Map<String, Object> controlsBody =
        objectMapper.readValue(controlsRequest.getBody().readUtf8(), new TypeReference<Map<String, Object>>() {});
    Map<String, Object> controlsPatch = (Map<String, Object>) controlsBody.get("controls_patch");
    assertEquals(120, ((Number) controlsPatch.get("timeout_seconds")).intValue());
    assertEquals(5, ((Number) controlsBody.get("expected_policy_generation")).intValue());

    client.updateIntentPolicy(
        "it_123",
        Map.of(
            "grants_patch",
            Map.of("delegate:agent://ops", Map.of("allow", new String[] {"resume", "update_controls"})),
            "envelope_patch",
            Map.of("max_retry_count", 10)),
        new RequestOptions(null, null, "agent://creator", null, null));
    RecordedRequest policyRequest = server.takeRequest();
    assertEquals("/v1/intents/it_123/policy?owner_agent=agent%3A%2F%2Fcreator", policyRequest.getPath());
    Map<String, Object> policyBody =
        objectMapper.readValue(policyRequest.getBody().readUtf8(), new TypeReference<Map<String, Object>>() {});
    Map<String, Object> grantsPatch = (Map<String, Object>) policyBody.get("grants_patch");
    Map<String, Object> delegateGrant = (Map<String, Object>) grantsPatch.get("delegate:agent://ops");
    assertTrue(((java.util.List<?>) delegateGrant.get("allow")).contains("resume"));
    assertTrue(((java.util.List<?>) delegateGrant.get("allow")).contains("update_controls"));
    Map<String, Object> envelopePatch = (Map<String, Object>) policyBody.get("envelope_patch");
    assertEquals(10, ((Number) envelopePatch.get("max_retry_count")).intValue());
  }

  @Test
  void accessAliasInboxInviteMediaSchemaWebhookEndpointsAreReachable() throws Exception {
    String accessRequestId = "ar_123";
    String aliasId = "al_123";
    String threadId = "thread_123";
    String token = "tok_123";
    String uploadId = "up_123";
    String subscriptionId = "wh_sub_123";
    String eventId = "evt_123";
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"access_request_id\":\"ar_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"items\":[]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"access_request\":{\"access_request_id\":\"ar_123\"}}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"state\":\"approved\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"alias_id\":\"al_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"items\":[]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"target\":\"agent://support\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"revoked\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"decision\":\"approve\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"capabilities\":[\"intent.submit\"]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"threads\":[]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"thread_id\":\"thread_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"changes\":[],\"next_cursor\":\"cur-2\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"token\":\"tok_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"token\":\"tok_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"accepted\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"upload_id\":\"up_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"upload_id\":\"up_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"status\":\"finalized\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"semantic_type\":\"notify.message.v1\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"semantic_type\":\"notify.message.v1\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"subscription_id\":\"wh_sub_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"items\":[]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"deleted\":true}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"event_id\":\"evt_123\"}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"replayed\":true}"));

    client.createAccessRequest(Map.of("owner_agent", "agent://owner"), new RequestOptions("ar-create-1", null));
    client.listAccessRequests("org_1", "ws_1", "pending", RequestOptions.none());
    client.getAccessRequest(accessRequestId, RequestOptions.none());
    client.reviewAccessRequest(accessRequestId, Map.of("decision", "approve"), new RequestOptions("ar-review-1", null));
    client.bindAlias(Map.of("alias", "@support", "target_agent", "agent://support"), new RequestOptions("alias-bind-1", null));
    client.listAliases("org_1", "ws_1", RequestOptions.none());
    client.resolveAlias("org_1", "ws_1", "@support", RequestOptions.none());
    client.revokeAlias(aliasId, new RequestOptions("alias-revoke-1", null));
    client.decideApproval("ap_123", Map.of("decision", "approve"), new RequestOptions("approval-1", null));
    client.getCapabilities(RequestOptions.none());
    client.listInbox("agent://owner", RequestOptions.none());
    client.getInboxThread(threadId, "agent://owner", RequestOptions.none());
    client.listInboxChanges("agent://owner", "cur-1", 50, RequestOptions.none());
    client.replyInboxThread(threadId, "ack", "agent://owner", new RequestOptions("reply-1", null));
    client.delegateInboxThread(threadId, Map.of("delegate_to", "agent://delegate"), "agent://owner", new RequestOptions("delegate-1", null));
    client.approveInboxThread(threadId, Map.of("comment", "approved"), "agent://owner", new RequestOptions("approve-1", null));
    client.rejectInboxThread(threadId, Map.of("comment", "reject"), "agent://owner", new RequestOptions("reject-1", null));
    client.deleteInboxMessages(threadId, Map.of("message_ids", new String[] {"m1", "m2"}), "agent://owner", new RequestOptions("delete-1", null));
    client.createInvite(Map.of("owner_agent", "agent://owner"), new RequestOptions("invite-create-1", null));
    client.getInvite(token, RequestOptions.none());
    client.acceptInvite(token, Map.of("owner_agent", "agent://owner"), new RequestOptions("invite-accept-1", null));
    client.createMediaUpload(Map.of("owner_agent", "agent://owner"), new RequestOptions("media-create-1", null));
    client.getMediaUpload(uploadId, RequestOptions.none());
    client.finalizeMediaUpload(Map.of("upload_id", uploadId), new RequestOptions("media-finalize-1", null));
    client.upsertSchema(
        Map.of("semantic_type", "notify.message.v1", "schema", Map.of("type", "object")),
        new RequestOptions("schema-upsert-1", null));
    client.getSchema("notify.message.v1", RequestOptions.none());
    client.upsertWebhookSubscription(
        Map.of(
            "owner_agent", "agent://owner",
            "callback_url", "https://example.com/hook",
            "event_types", new String[] {"inbox.thread_created"}),
        new RequestOptions("wh-upsert-1", null));
    client.listWebhookSubscriptions("agent://owner", RequestOptions.none());
    client.deleteWebhookSubscription(subscriptionId, "agent://owner", RequestOptions.none());
    client.publishWebhookEvent(
        Map.of("event_type", "inbox.thread_created", "payload", Map.of("thread_id", "thr_1")),
        "agent://owner",
        new RequestOptions("wh-publish-1", null));
    client.replayWebhookEvent(eventId, "agent://owner", new RequestOptions("wh-replay-1", null));

    assertEquals("/v1/access-requests", server.takeRequest().getPath());
    assertEquals("/v1/access-requests?org_id=org_1&workspace_id=ws_1&state=pending", server.takeRequest().getPath());
    assertEquals("/v1/access-requests/ar_123", server.takeRequest().getPath());
    assertEquals("/v1/access-requests/ar_123/review", server.takeRequest().getPath());
    assertEquals("/v1/aliases", server.takeRequest().getPath());
    assertEquals("/v1/aliases?org_id=org_1&workspace_id=ws_1", server.takeRequest().getPath());
    assertEquals("/v1/aliases/resolve?org_id=org_1&workspace_id=ws_1&alias=%40support", server.takeRequest().getPath());
    assertEquals("/v1/aliases/al_123/revoke", server.takeRequest().getPath());
    assertEquals("/v1/approvals/ap_123/decision", server.takeRequest().getPath());
    assertEquals("/v1/capabilities", server.takeRequest().getPath());
    assertEquals("/v1/inbox?owner_agent=agent%3A%2F%2Fowner", server.takeRequest().getPath());
    assertEquals("/v1/inbox/thread_123?owner_agent=agent%3A%2F%2Fowner", server.takeRequest().getPath());
    assertEquals("/v1/inbox/changes?owner_agent=agent%3A%2F%2Fowner&cursor=cur-1&limit=50", server.takeRequest().getPath());
  }

  @Test
  void organizationRoutingDeliveryAndBillingEndpointsAreReachable() throws Exception {
    String orgId = "org_1";
    String workspaceId = "ws_1";
    String memberId = "member_1";
    String principalId = "pr_1";
    String routeId = "route_1";
    String bindingId = "binding_1";
    String deliveryId = "delivery_1";
    String invoiceId = "inv_1";

    for (int i = 0; i < 32; i++) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    }

    client.createOrganization(Map.of("org_id", orgId, "name", "Acme"), new RequestOptions("org-create-1", null));
    client.getOrganization(orgId, RequestOptions.none());
    client.updateOrganization(orgId, Map.of("display_name", "Acme Inc"), new RequestOptions("org-update-1", null));
    client.createWorkspace(orgId, Map.of("workspace_id", workspaceId, "name", "Primary"), new RequestOptions("ws-create-1", null));
    client.listWorkspaces(orgId, RequestOptions.none());
    client.updateWorkspace(orgId, workspaceId, Map.of("name", "Primary Updated"), new RequestOptions("ws-update-1", null));
    client.listOrganizationMembers(orgId, workspaceId, RequestOptions.none());
    client.addOrganizationMember(orgId, Map.of("owner_agent", "agent://owner", "role", "workspace_admin"), new RequestOptions("member-add-1", null));
    client.updateOrganizationMember(orgId, memberId, Map.of("role", "workspace_viewer"), new RequestOptions("member-update-1", null));
    client.removeOrganizationMember(orgId, memberId, RequestOptions.none());
    client.updateQuota(Map.of("org_id", orgId, "workspace_id", workspaceId, "hard_enforce", true), new RequestOptions("quota-update-1", null));
    client.getQuota(orgId, workspaceId, RequestOptions.none());
    client.getUsageSummary(orgId, workspaceId, "30d", RequestOptions.none());
    client.getUsageTimeseries(orgId, workspaceId, 7, RequestOptions.none());
    client.createPrincipal(Map.of("owner_agent", "agent://owner", "kind", "service"), new RequestOptions("principal-create-1", null));
    client.getPrincipal(principalId, RequestOptions.none());
    client.registerRoutingEndpoint(Map.of("org_id", orgId, "workspace_id", workspaceId, "transport", "http"), new RequestOptions("route-register-1", null));
    client.listRoutingEndpoints(orgId, workspaceId, RequestOptions.none());
    client.updateRoutingEndpoint(routeId, Map.of("weight", 10), new RequestOptions("route-update-1", null));
    client.removeRoutingEndpoint(routeId, RequestOptions.none());
    client.resolveRouting(Map.of("org_id", orgId, "workspace_id", workspaceId, "semantic_type", "notify.message.v1"), new RequestOptions("route-resolve-1", null));
    client.upsertTransportBinding(Map.of("org_id", orgId, "workspace_id", workspaceId, "transport", "http"), new RequestOptions("binding-upsert-1", null));
    client.listTransportBindings(orgId, workspaceId, RequestOptions.none());
    client.removeTransportBinding(bindingId, RequestOptions.none());
    client.submitDelivery(Map.of("org_id", orgId, "workspace_id", workspaceId, "principal_id", principalId), new RequestOptions("delivery-submit-1", null));
    client.listDeliveries(orgId, workspaceId, principalId, "pending", RequestOptions.none());
    client.getDelivery(deliveryId, RequestOptions.none());
    client.replayDelivery(deliveryId, new RequestOptions("delivery-replay-1", null));
    client.updateBillingPlan(Map.of("org_id", orgId, "workspace_id", workspaceId, "plan", "enterprise"), new RequestOptions("billing-plan-1", null));
    client.getBillingPlan(orgId, workspaceId, RequestOptions.none());
    client.listBillingInvoices(orgId, workspaceId, "open", RequestOptions.none());
    client.getBillingInvoice(invoiceId, RequestOptions.none());

    assertEquals("/v1/organizations", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/workspaces", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/workspaces", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/workspaces/ws_1", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/members?workspace_id=ws_1", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/members", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/members/member_1", server.takeRequest().getPath());
    assertEquals("/v1/organizations/org_1/members/member_1", server.takeRequest().getPath());
    assertEquals("/v1/quotas", server.takeRequest().getPath());
    assertEquals("/v1/quotas?org_id=org_1&workspace_id=ws_1", server.takeRequest().getPath());
    assertEquals("/v1/usage/summary?org_id=org_1&workspace_id=ws_1&window=30d", server.takeRequest().getPath());
    assertEquals("/v1/usage/timeseries?org_id=org_1&workspace_id=ws_1&window_days=7", server.takeRequest().getPath());
    assertEquals("/v1/principals", server.takeRequest().getPath());
    assertEquals("/v1/principals/pr_1", server.takeRequest().getPath());
    assertEquals("/v1/routing/endpoints", server.takeRequest().getPath());
    assertEquals("/v1/routing/endpoints?org_id=org_1&workspace_id=ws_1", server.takeRequest().getPath());
    assertEquals("/v1/routing/endpoints/route_1", server.takeRequest().getPath());
    assertEquals("/v1/routing/endpoints/route_1", server.takeRequest().getPath());
    assertEquals("/v1/routing/resolve", server.takeRequest().getPath());
    assertEquals("/v1/transports/bindings", server.takeRequest().getPath());
    assertEquals("/v1/transports/bindings?org_id=org_1&workspace_id=ws_1", server.takeRequest().getPath());
    assertEquals("/v1/transports/bindings/binding_1", server.takeRequest().getPath());
    assertEquals("/v1/deliveries", server.takeRequest().getPath());
    assertEquals("/v1/deliveries?org_id=org_1&workspace_id=ws_1&principal_id=pr_1&status=pending", server.takeRequest().getPath());
    assertEquals("/v1/deliveries/delivery_1", server.takeRequest().getPath());
    assertEquals("/v1/deliveries/delivery_1/replay", server.takeRequest().getPath());
    assertEquals("/v1/billing/plan", server.takeRequest().getPath());
    assertEquals("/v1/billing/plan?org_id=org_1&workspace_id=ws_1", server.takeRequest().getPath());
    assertEquals("/v1/billing/invoices?org_id=org_1&workspace_id=ws_1&status=open", server.takeRequest().getPath());
    assertEquals("/v1/billing/invoices/inv_1", server.takeRequest().getPath());
  }

  @Test
  void observeCollectsEventsAndStopsAtTerminal() throws Exception {
    // First poll: two non-terminal events
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":1,\"event_type\":\"intent.created\",\"status\":\"PENDING\"},"
        + "{\"seq\":2,\"event_type\":\"intent.dispatched\",\"status\":\"IN_PROGRESS\"}"
        + "]}"));
    // Second poll: empty (triggers sleep + re-poll)
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":[]}"));
    // Third poll: terminal event
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":3,\"event_type\":\"intent.completed\",\"status\":\"COMPLETED\"}"
        + "]}"));

    List<Map<String, Object>> events = client.observe("it_obs_1",
        new ObserveOptions(0, 0.05, null));

    assertEquals(3, events.size());
    assertEquals("intent.created", events.get(0).get("event_type"));
    assertEquals("intent.dispatched", events.get(1).get("event_type"));
    assertEquals("intent.completed", events.get(2).get("event_type"));
    assertEquals("COMPLETED", events.get(2).get("status"));

    // Verify since parameter advances: first call since=0, third since=2
    RecordedRequest req1 = server.takeRequest();
    assertEquals("/v1/intents/it_obs_1/events?since=0", req1.getPath());
    RecordedRequest req2 = server.takeRequest();
    assertEquals("/v1/intents/it_obs_1/events?since=2", req2.getPath());
    RecordedRequest req3 = server.takeRequest();
    assertEquals("/v1/intents/it_obs_1/events?since=2", req3.getPath());
  }

  @Test
  void observeStopsOnEventTypeAlone() throws Exception {
    // Terminal via event_type only (no status field)
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":1,\"event_type\":\"intent.failed\"}"
        + "]}"));

    List<Map<String, Object>> events = client.observe("it_obs_2",
        new ObserveOptions(0, 0.05, null));

    assertEquals(1, events.size());
    assertEquals("intent.failed", events.get(0).get("event_type"));
  }

  @Test
  void observeStopsOnStatusAlone() throws Exception {
    // Terminal via status only (no event_type field)
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":1,\"status\":\"CANCELED\"}"
        + "]}"));

    List<Map<String, Object>> events = client.observe("it_obs_3",
        new ObserveOptions(0, 0.05, null));

    assertEquals(1, events.size());
    assertEquals("CANCELED", events.get(0).get("status"));
  }

  @Test
  void observeTimesOut() {
    // Enqueue enough empty responses to keep polling
    for (int i = 0; i < 20; i++) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(
          "{\"ok\":true,\"events\":[]}"));
    }

    AxmeTimeoutException ex = assertThrows(AxmeTimeoutException.class, () ->
        client.observe("it_timeout", new ObserveOptions(0, 0.05, 0.1)));

    assertEquals("it_timeout", ex.getIntentId());
    assertTrue(ex.getTimeoutSeconds() > 0);
  }

  @Test
  void waitForReturnsTerminalEvent() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":1,\"event_type\":\"intent.created\",\"status\":\"PENDING\"}"
        + "]}"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":2,\"event_type\":\"intent.timed_out\",\"status\":\"TIMED_OUT\"}"
        + "]}"));

    Map<String, Object> terminal = client.waitFor("it_wait_1",
        new ObserveOptions(0, 0.05, null));

    assertNotNull(terminal);
    assertEquals("TIMED_OUT", terminal.get("status"));
    assertEquals("intent.timed_out", terminal.get("event_type"));
  }

  @Test
  void observeWithSinceParameter() throws Exception {
    // Start from since=5, verify first request uses since=5
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":6,\"event_type\":\"intent.completed\",\"status\":\"COMPLETED\"}"
        + "]}"));

    List<Map<String, Object>> events = client.observe("it_since",
        new ObserveOptions(5, 0.05, null));

    assertEquals(1, events.size());
    RecordedRequest req = server.takeRequest();
    assertEquals("/v1/intents/it_since/events?since=5", req.getPath());
  }

  @Test
  void observeWithNullOptionsUsesDefaults() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody(
        "{\"ok\":true,\"events\":["
        + "{\"seq\":1,\"event_type\":\"intent.completed\",\"status\":\"COMPLETED\"}"
        + "]}"));

    List<Map<String, Object>> events = client.observe("it_null_opts", null);

    assertEquals(1, events.size());
    RecordedRequest req = server.takeRequest();
    assertEquals("/v1/intents/it_null_opts/events?since=0", req.getPath());
  }
}
