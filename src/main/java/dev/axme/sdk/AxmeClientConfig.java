package dev.axme.sdk;

public final class AxmeClientConfig {
  public static final String DEFAULT_BASE_URL = "https://api.cloud.axme.ai";

  private final String baseUrl;
  private final String apiKey;
  private final String actorToken;

  public static AxmeClientConfig forCloud(String apiKey) {
    return new AxmeClientConfig(DEFAULT_BASE_URL, apiKey, null, null);
  }

  public static AxmeClientConfig forCloud(String apiKey, String actorToken) {
    return new AxmeClientConfig(DEFAULT_BASE_URL, apiKey, actorToken, null);
  }

  public AxmeClientConfig(String baseUrl, String apiKey) {
    this(baseUrl, apiKey, null, null);
  }

  public AxmeClientConfig(String baseUrl, String apiKey, String actorToken) {
    this(baseUrl, apiKey, actorToken, null);
  }

  public AxmeClientConfig(String baseUrl, String apiKey, String actorToken, String bearerToken) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalArgumentException("apiKey is required");
    }
    String normalizedActorToken = actorToken == null ? null : actorToken.trim();
    String normalizedBearerToken = bearerToken == null ? null : bearerToken.trim();
    if (isNonBlank(normalizedActorToken)
        && isNonBlank(normalizedBearerToken)
        && !normalizedActorToken.equals(normalizedBearerToken)) {
      throw new IllegalArgumentException("actorToken and bearerToken must match when both are provided");
    }
    String normalizedBaseUrl = (baseUrl == null || baseUrl.trim().isEmpty()) ? DEFAULT_BASE_URL : baseUrl.trim();
    this.baseUrl = trimTrailingSlash(normalizedBaseUrl);
    this.apiKey = apiKey.trim();
    this.actorToken = isNonBlank(normalizedActorToken) ? normalizedActorToken : normalizedBearerToken;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getActorToken() {
    return actorToken;
  }

  private static boolean isNonBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String trimTrailingSlash(String value) {
    if (value.endsWith("/")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }
}
