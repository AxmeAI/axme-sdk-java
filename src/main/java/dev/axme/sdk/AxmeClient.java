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
