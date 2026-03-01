package dev.axme.sdk;

public final class AxmeHttpException extends RuntimeException {
  private final int statusCode;
  private final String responseBody;

  public AxmeHttpException(int statusCode, String responseBody) {
    super("axme request failed with status " + statusCode);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getResponseBody() {
    return responseBody;
  }
}
