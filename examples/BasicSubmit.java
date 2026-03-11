import dev.axme.sdk.AxmeClient;
import dev.axme.sdk.AxmeClientConfig;
import dev.axme.sdk.RequestOptions;

import java.util.Map;
import java.util.UUID;

public final class BasicSubmit {
  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("AXME_API_KEY");
    String baseUrl = System.getenv("AXME_BASE_URL");
    AxmeClientConfig config = (baseUrl == null || baseUrl.isBlank())
        ? AxmeClientConfig.forCloud(apiKey)
        : new AxmeClientConfig(baseUrl, apiKey);

    AxmeClient client = new AxmeClient(config);
    Map<String, Object> created = client.createIntent(
        Map.of(
            "intent_type", "intent.demo.v1",
            "correlation_id", UUID.randomUUID().toString(),
            "to_agent", "agent://acme-corp/production/target",
            "payload", Map.of("task", "hello-from-java")
        ),
        RequestOptions.none()
    );
    String intentId = (String) created.get("intent_id");
    Map<String, Object> current = client.getIntent(intentId, RequestOptions.none());
    Object intentObj = current.get("intent");
    if (intentObj instanceof Map<?, ?> intentMap) {
      Object status = intentMap.get("status");
      if (status == null) {
        status = intentMap.get("lifecycle_status");
      }
      System.out.println(status == null ? "UNKNOWN" : String.valueOf(status));
      return;
    }
    Object status = current.get("status");
    System.out.println(status == null ? "UNKNOWN" : String.valueOf(status));
  }
}
