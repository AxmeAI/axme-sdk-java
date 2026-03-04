package dev.axme.sdk;

public final class RequestOptions {
  private final String idempotencyKey;
  private final String traceId;
  private final String ownerAgent;
  private final String xOwnerAgent;
  private final String authorization;

  public RequestOptions() {
    this(null, null, null, null, null);
  }

  public RequestOptions(String idempotencyKey, String traceId) {
    this(idempotencyKey, traceId, null, null, null);
  }

  public RequestOptions(
      String idempotencyKey,
      String traceId,
      String ownerAgent,
      String xOwnerAgent,
      String authorization) {
    this.idempotencyKey = idempotencyKey;
    this.traceId = traceId;
    this.ownerAgent = ownerAgent;
    this.xOwnerAgent = xOwnerAgent;
    this.authorization = authorization;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getOwnerAgent() {
    return ownerAgent;
  }

  public String getXOwnerAgent() {
    return xOwnerAgent;
  }

  public String getAuthorization() {
    return authorization;
  }

  public static RequestOptions none() {
    return new RequestOptions();
  }
}
