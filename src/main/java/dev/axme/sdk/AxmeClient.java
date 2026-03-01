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
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/json");

    if (payload != null) {
      builder.header("Content-Type", "application/json");
    }
    if (!isBlank(options.getIdempotencyKey())) {
      builder.header("Idempotency-Key", options.getIdempotencyKey());
    }
    if (!isBlank(options.getTraceId())) {
      builder.header("X-Trace-Id", options.getTraceId());
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
