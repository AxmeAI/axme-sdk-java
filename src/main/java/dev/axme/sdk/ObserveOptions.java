package dev.axme.sdk;

/**
 * Options for {@link AxmeClient#observe} and {@link AxmeClient#waitFor} polling methods.
 */
public final class ObserveOptions {
  private final int since;
  private final double pollIntervalSeconds;
  private final Double timeoutSeconds;

  public ObserveOptions() {
    this(0, 1.0, null);
  }

  public ObserveOptions(int since, double pollIntervalSeconds, Double timeoutSeconds) {
    if (pollIntervalSeconds <= 0) {
      throw new IllegalArgumentException("pollIntervalSeconds must be positive");
    }
    if (timeoutSeconds != null && timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
    this.since = since;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.timeoutSeconds = timeoutSeconds;
  }

  public int getSince() {
    return since;
  }

  public double getPollIntervalSeconds() {
    return pollIntervalSeconds;
  }

  public Double getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public static ObserveOptions defaults() {
    return new ObserveOptions();
  }
}
