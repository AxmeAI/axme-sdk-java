package dev.axme.sdk;

/**
 * Thrown when an {@link AxmeClient#observe} or {@link AxmeClient#waitFor} call
 * exceeds its configured timeout.
 */
public final class AxmeTimeoutException extends RuntimeException {
  private final String intentId;
  private final double timeoutSeconds;

  public AxmeTimeoutException(String intentId, double timeoutSeconds) {
    super("observe timed out after " + timeoutSeconds + "s for intent " + intentId);
    this.intentId = intentId;
    this.timeoutSeconds = timeoutSeconds;
  }

  public String getIntentId() {
    return intentId;
  }

  public double getTimeoutSeconds() {
    return timeoutSeconds;
  }
}
