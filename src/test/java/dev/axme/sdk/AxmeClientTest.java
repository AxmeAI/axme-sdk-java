package dev.axme.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    assertEquals("Bearer token", request.getHeader("Authorization"));
    assertEquals("register-1", request.getHeader("Idempotency-Key"));

    Map<String, Object> body =
        objectMapper.readValue(request.getBody().readUtf8(), new TypeReference<Map<String, Object>>() {});
    assertEquals("@partner.user", body.get("nick"));
    assertTrue((Boolean) response.get("ok"));
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
    assertEquals("/v1/intents/it_123/controls", server.takeRequest().getPath());

    client.updateIntentPolicy(
        "it_123",
        Map.of(
            "grants_patch",
            Map.of("delegate:agent://ops", Map.of("allow", new String[] {"resume", "update_controls"})),
            "envelope_patch",
            Map.of("max_retry_count", 10)),
        new RequestOptions(null, null, "agent://creator", null, null));
    assertEquals("/v1/intents/it_123/policy?owner_agent=agent%3A%2F%2Fcreator", server.takeRequest().getPath());
  }
}
