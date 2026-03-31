package dev.axme.sdk;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent Mesh operations - heartbeat, health monitoring, metrics reporting,
 * agent management, and event listing.
 *
 * <p>Obtain an instance via {@link AxmeClient#getMesh()}.
 */
public final class MeshClient {
  private final AxmeClient client;
  private volatile Thread heartbeatThread;
  private volatile boolean heartbeatStopRequested;
  private final ConcurrentHashMap<String, AtomicReference<Double>> metricsBuffer = new ConcurrentHashMap<>();
  private final AtomicInteger intentsTotal = new AtomicInteger(0);
  private final AtomicInteger intentsSucceeded = new AtomicInteger(0);
  private final AtomicInteger intentsFailed = new AtomicInteger(0);

  MeshClient(AxmeClient client) {
    this.client = client;
  }

  // -- Heartbeat ---------------------------------------------------------------

  /**
   * Send a single heartbeat to the mesh. Optionally include metrics.
   *
   * @param metrics optional metrics map to include in the heartbeat
   * @param traceId optional trace-id header value
   * @return the gateway response body
   */
  public Map<String, Object> heartbeat(Map<String, Object> metrics, String traceId)
      throws IOException, InterruptedException {
    Map<String, Object> body = new LinkedHashMap<>();
    if (metrics != null && !metrics.isEmpty()) {
      body.put("metrics", metrics);
    }
    RequestOptions opts = traceId != null ? new RequestOptions(null, traceId) : RequestOptions.none();
    return client.requestJson(
        "POST",
        "/v1/mesh/heartbeat",
        Map.of(),
        body.isEmpty() ? null : body,
        opts);
  }

  /**
   * Start a background daemon thread that sends heartbeats at regular intervals.
   *
   * <p>If a heartbeat thread is already running, this method is a no-op.
   *
   * @param intervalSeconds seconds between heartbeats (default 30)
   * @param includeMetrics  whether to flush and include buffered metrics with each heartbeat
   */
  public synchronized void startHeartbeat(double intervalSeconds, boolean includeMetrics) {
    if (heartbeatThread != null && heartbeatThread.isAlive()) {
      return; // already running
    }
    heartbeatStopRequested = false;

    heartbeatThread = new Thread(() -> {
      while (!heartbeatStopRequested) {
        try {
          Thread.sleep((long) (intervalSeconds * 1000));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        if (heartbeatStopRequested) {
          return;
        }
        try {
          Map<String, Object> metrics = includeMetrics ? flushMetrics() : null;
          heartbeat(metrics, null);
        } catch (Exception e) {
          // Heartbeat failures are non-fatal
        }
      }
    }, "axme-mesh-heartbeat");
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
  }

  /**
   * Start heartbeat with default settings (30s interval, include metrics).
   */
  public void startHeartbeat() {
    startHeartbeat(30.0, true);
  }

  /**
   * Stop the background heartbeat thread. Blocks up to 5 seconds for the
   * thread to terminate.
   */
  public synchronized void stopHeartbeat() {
    heartbeatStopRequested = true;
    Thread thread = heartbeatThread;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      heartbeatThread = null;
    }
  }

  // -- Metrics -----------------------------------------------------------------

  /**
   * Buffer a metric observation. Flushed with next heartbeat.
   *
   * @param success   whether the intent succeeded
   * @param latencyMs latency in milliseconds (may be null)
   * @param costUsd   cost in USD (may be null)
   */
  public void reportMetric(boolean success, Double latencyMs, Double costUsd) {
    int total = intentsTotal.incrementAndGet();
    if (success) {
      intentsSucceeded.incrementAndGet();
    } else {
      intentsFailed.incrementAndGet();
    }
    if (latencyMs != null) {
      metricsBuffer.compute("avg_latency_ms", (key, ref) -> {
        if (ref == null) {
          ref = new AtomicReference<>(0.0);
        }
        double prevAvg = ref.get();
        ref.set(prevAvg + (latencyMs - prevAvg) / total);
        return ref;
      });
    }
    if (costUsd != null) {
      metricsBuffer.compute("cost_usd", (key, ref) -> {
        if (ref == null) {
          ref = new AtomicReference<>(0.0);
        }
        ref.set(ref.get() + costUsd);
        return ref;
      });
    }
  }

  /**
   * Flush the buffered metrics and return them, or null if nothing was buffered.
   */
  Map<String, Object> flushMetrics() {
    int total = intentsTotal.getAndSet(0);
    int succeeded = intentsSucceeded.getAndSet(0);
    int failed = intentsFailed.getAndSet(0);
    if (total == 0 && metricsBuffer.isEmpty()) {
      return null;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    if (total > 0) {
      result.put("intents_total", total);
    }
    if (succeeded > 0) {
      result.put("intents_succeeded", succeeded);
    }
    if (failed > 0) {
      result.put("intents_failed", failed);
    }
    AtomicReference<Double> avgRef = metricsBuffer.remove("avg_latency_ms");
    if (avgRef != null) {
      result.put("avg_latency_ms", avgRef.get());
    }
    AtomicReference<Double> costRef = metricsBuffer.remove("cost_usd");
    if (costRef != null) {
      result.put("cost_usd", costRef.get());
    }
    return result.isEmpty() ? null : result;
  }

  // -- Agent management --------------------------------------------------------

  /**
   * List all agents in the workspace with health status.
   *
   * @param limit  maximum number of agents to return
   * @param health optional health filter (e.g. "healthy", "degraded", "dead")
   * @return gateway response body
   */
  public Map<String, Object> listAgents(int limit, String health)
      throws IOException, InterruptedException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("limit", String.valueOf(limit));
    if (health != null && !health.trim().isEmpty()) {
      params.put("health", health);
    }
    return client.requestJson("GET", "/v1/mesh/agents", params, null, RequestOptions.none());
  }

  /**
   * List agents with default limit of 100 and no health filter.
   */
  public Map<String, Object> listAgents() throws IOException, InterruptedException {
    return listAgents(100, null);
  }

  /**
   * Get single agent detail with metrics and events.
   *
   * @param addressId the agent address ID
   * @return gateway response body
   */
  public Map<String, Object> getAgent(String addressId)
      throws IOException, InterruptedException {
    return client.requestJson(
        "GET",
        "/v1/mesh/agents/" + addressId,
        Map.of(),
        null,
        RequestOptions.none());
  }

  /**
   * Kill an agent - block all intents to and from it.
   *
   * @param addressId the agent address ID to kill
   * @return gateway response body
   */
  public Map<String, Object> kill(String addressId)
      throws IOException, InterruptedException {
    return client.requestJson(
        "POST",
        "/v1/mesh/agents/" + addressId + "/kill",
        Map.of(),
        null,
        RequestOptions.none());
  }

  /**
   * Resume a killed agent.
   *
   * @param addressId the agent address ID to resume
   * @return gateway response body
   */
  public Map<String, Object> resume(String addressId)
      throws IOException, InterruptedException {
    return client.requestJson(
        "POST",
        "/v1/mesh/agents/" + addressId + "/resume",
        Map.of(),
        null,
        RequestOptions.none());
  }

  // -- Events ------------------------------------------------------------------

  /**
   * List recent mesh events (kills, resumes, health changes).
   *
   * @param limit     maximum number of events to return
   * @param eventType optional filter by event type
   * @return gateway response body
   */
  public Map<String, Object> listEvents(int limit, String eventType)
      throws IOException, InterruptedException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("limit", String.valueOf(limit));
    if (eventType != null && !eventType.trim().isEmpty()) {
      params.put("event_type", eventType);
    }
    return client.requestJson("GET", "/v1/mesh/events", params, null, RequestOptions.none());
  }

  /**
   * List events with default limit of 50 and no type filter.
   */
  public Map<String, Object> listEvents() throws IOException, InterruptedException {
    return listEvents(50, null);
  }
}
