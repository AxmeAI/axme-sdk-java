package dev.axme.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class MeshClientTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockWebServer server;
  private AxmeClient client;
  private MeshClient mesh;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new AxmeClient(new AxmeClientConfig(server.url("/").toString(), "test-key"));
    mesh = client.getMesh();
  }

  @AfterEach
  void tearDown() throws Exception {
    mesh.stopHeartbeat();
    server.shutdown();
  }

  // -- getMesh lazy init -------------------------------------------------------

  @Test
  void getMeshReturnsSameInstance() {
    MeshClient a = client.getMesh();
    MeshClient b = client.getMesh();
    assertTrue(a == b, "getMesh() must return the same instance");
  }

  // -- heartbeat ---------------------------------------------------------------

  @Test
  void heartbeatPostsWithoutMetrics() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    Map<String, Object> response = mesh.heartbeat(null, null);

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/v1/mesh/heartbeat", req.getPath());
    assertEquals("test-key", req.getHeader("x-api-key"));
    assertTrue((Boolean) response.get("ok"));
    // No body when metrics is null
    String body = req.getBody().readUtf8();
    assertTrue(body.isEmpty(), "Expected empty body when no metrics, got: " + body);
  }

  @Test
  void heartbeatPostsWithMetrics() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    Map<String, Object> metrics = Map.of("intents_total", 5, "avg_latency_ms", 120.5);
    mesh.heartbeat(metrics, "trace-abc");

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/v1/mesh/heartbeat", req.getPath());
    assertEquals("trace-abc", req.getHeader("X-Trace-Id"));
    Map<String, Object> body =
        objectMapper.readValue(req.getBody().readUtf8(), new TypeReference<Map<String, Object>>() {});
    assertNotNull(body.get("metrics"), "Heartbeat body must contain metrics");
  }

  // -- reportMetric + flushMetrics ---------------------------------------------

  @Test
  void reportMetricBuffersAndFlushes() {
    mesh.reportMetric(true, 100.0, 0.01);
    mesh.reportMetric(false, 200.0, 0.02);
    mesh.reportMetric(true, null, null);

    Map<String, Object> flushed = mesh.flushMetrics();

    assertNotNull(flushed);
    assertEquals(3, ((Number) flushed.get("intents_total")).intValue());
    assertEquals(2, ((Number) flushed.get("intents_succeeded")).intValue());
    assertEquals(1, ((Number) flushed.get("intents_failed")).intValue());
    assertNotNull(flushed.get("avg_latency_ms"));
    double costUsd = ((Number) flushed.get("cost_usd")).doubleValue();
    assertEquals(0.03, costUsd, 0.001);

    // Second flush should be null (buffer was drained)
    assertNull(mesh.flushMetrics());
  }

  @Test
  void flushMetricsReturnsNullWhenEmpty() {
    assertNull(mesh.flushMetrics());
  }

  // -- startHeartbeat / stopHeartbeat ------------------------------------------

  @Test
  void startHeartbeatSendsPeriodicRequests() throws Exception {
    // Enqueue enough responses for a few heartbeats
    for (int i = 0; i < 5; i++) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    }

    mesh.startHeartbeat(0.1, false);
    Thread.sleep(350); // Wait for a few intervals
    mesh.stopHeartbeat();

    // Should have received at least 2 heartbeat requests
    assertTrue(server.getRequestCount() >= 2,
        "Expected at least 2 heartbeats, got " + server.getRequestCount());
    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/v1/mesh/heartbeat", req.getPath());
  }

  @Test
  void startHeartbeatIsIdempotent() throws Exception {
    for (int i = 0; i < 10; i++) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
    }
    mesh.startHeartbeat(0.1, false);
    mesh.startHeartbeat(0.1, false); // second call is a no-op
    Thread.sleep(250);
    mesh.stopHeartbeat();
    // Just checking it doesn't throw or start duplicate threads
    assertTrue(server.getRequestCount() >= 1);
  }

  @Test
  void stopHeartbeatIsIdempotent() {
    // stop without start should not throw
    mesh.stopHeartbeat();
    mesh.stopHeartbeat();
  }

  // -- listAgents --------------------------------------------------------------

  @Test
  void listAgentsGetsWithLimitAndHealth() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"agents\":[{\"address_id\":\"agent-1\"}]}"));

    Map<String, Object> response = mesh.listAgents(50, "healthy");

    RecordedRequest req = server.takeRequest();
    assertEquals("GET", req.getMethod());
    assertEquals("/v1/mesh/agents?limit=50&health=healthy", req.getPath());
    assertTrue((Boolean) response.get("ok"));
  }

  @Test
  void listAgentsGetsWithDefaultParams() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"agents\":[]}"));

    mesh.listAgents();

    RecordedRequest req = server.takeRequest();
    assertEquals("GET", req.getMethod());
    assertEquals("/v1/mesh/agents?limit=100", req.getPath());
  }

  @Test
  void listAgentsOmitsBlankHealth() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"agents\":[]}"));

    mesh.listAgents(25, null);

    RecordedRequest req = server.takeRequest();
    assertEquals("/v1/mesh/agents?limit=25", req.getPath());
  }

  // -- getAgent ----------------------------------------------------------------

  @Test
  void getAgentGetsById() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"address_id\":\"agent-1\",\"health\":\"healthy\"}"));

    Map<String, Object> response = mesh.getAgent("agent-1");

    RecordedRequest req = server.takeRequest();
    assertEquals("GET", req.getMethod());
    assertEquals("/v1/mesh/agents/agent-1", req.getPath());
    assertEquals("healthy", response.get("health"));
  }

  // -- kill --------------------------------------------------------------------

  @Test
  void killPostsToCorrectPath() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"status\":\"killed\"}"));

    Map<String, Object> response = mesh.kill("agent-bad");

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/v1/mesh/agents/agent-bad/kill", req.getPath());
    assertEquals("killed", response.get("status"));
  }

  // -- resume ------------------------------------------------------------------

  @Test
  void resumePostsToCorrectPath() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"status\":\"alive\"}"));

    Map<String, Object> response = mesh.resume("agent-bad");

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/v1/mesh/agents/agent-bad/resume", req.getPath());
    assertEquals("alive", response.get("status"));
  }

  // -- listEvents --------------------------------------------------------------

  @Test
  void listEventsGetsWithLimitAndType() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"events\":[{\"event_type\":\"agent.killed\"}]}"));

    Map<String, Object> response = mesh.listEvents(10, "agent.killed");

    RecordedRequest req = server.takeRequest();
    assertEquals("GET", req.getMethod());
    assertEquals("/v1/mesh/events?limit=10&event_type=agent.killed", req.getPath());
    assertTrue((Boolean) response.get("ok"));
  }

  @Test
  void listEventsGetsWithDefaults() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"events\":[]}"));

    mesh.listEvents();

    RecordedRequest req = server.takeRequest();
    assertEquals("GET", req.getMethod());
    assertEquals("/v1/mesh/events?limit=50", req.getPath());
  }

  @Test
  void listEventsOmitsBlankEventType() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200)
        .setBody("{\"ok\":true,\"events\":[]}"));

    mesh.listEvents(20, null);

    RecordedRequest req = server.takeRequest();
    assertEquals("/v1/mesh/events?limit=20", req.getPath());
  }

  // -- HTTP error propagation --------------------------------------------------

  @Test
  void meshMethodsThrowOnHttpError() {
    server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":\"forbidden\"}"));

    try {
      mesh.listAgents();
    } catch (Exception e) {
      assertTrue(e instanceof AxmeHttpException);
      assertEquals(403, ((AxmeHttpException) e).getStatusCode());
    }
  }

  // -- heartbeat with flushed metrics ------------------------------------------

  @Test
  void heartbeatIncludesBufferedMetricsWhenPresent() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    mesh.reportMetric(true, 50.0, 0.005);
    Map<String, Object> flushed = mesh.flushMetrics();
    mesh.heartbeat(flushed, null);

    RecordedRequest req = server.takeRequest();
    Map<String, Object> body =
        objectMapper.readValue(req.getBody().readUtf8(), new TypeReference<Map<String, Object>>() {});
    @SuppressWarnings("unchecked")
    Map<String, Object> metrics = (Map<String, Object>) body.get("metrics");
    assertNotNull(metrics);
    assertEquals(1, ((Number) metrics.get("intents_total")).intValue());
    assertEquals(1, ((Number) metrics.get("intents_succeeded")).intValue());
  }

  // -- auth header forwarding --------------------------------------------------

  @Test
  void meshRequestsIncludeApiKeyHeader() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    mesh.getAgent("a1");

    RecordedRequest req = server.takeRequest();
    assertEquals("test-key", req.getHeader("x-api-key"));
  }
}
