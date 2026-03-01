package dev.axme.sdk;

public final class RequestOptions {
  private final String idempotencyKey;
  private final String traceId;

  public RequestOptions() {
    this(null, null);
  }

  public RequestOptions(String idempotencyKey, String traceId) {
    this.idempotencyKey = idempotencyKey;
    this.traceId = traceId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getTraceId() {
    return traceId;
  }

  public static RequestOptions none() {
    return new RequestOptions();
  }
}
