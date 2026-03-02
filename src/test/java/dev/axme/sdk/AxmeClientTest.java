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
}
