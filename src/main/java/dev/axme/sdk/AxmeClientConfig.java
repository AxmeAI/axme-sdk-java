package dev.axme.sdk;

public final class AxmeClientConfig {
  private final String baseUrl;
  private final String apiKey;

  public AxmeClientConfig(String baseUrl, String apiKey) {
    if (baseUrl == null || baseUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("baseUrl is required");
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalArgumentException("apiKey is required");
    }
    this.baseUrl = trimTrailingSlash(baseUrl.trim());
    this.apiKey = apiKey.trim();
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  private static String trimTrailingSlash(String value) {
    if (value.endsWith("/")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }
}
