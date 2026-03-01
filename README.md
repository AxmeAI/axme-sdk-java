# axme-sdk-java

Official Java SDK for Axme APIs and workflows.

## Status

Beta parity kickoff in progress.

## Quickstart

```java
import dev.axme.sdk.AxmeClient;
import dev.axme.sdk.AxmeClientConfig;
import dev.axme.sdk.RequestOptions;
import java.util.Map;

public class Quickstart {
  public static void main(String[] args) throws Exception {
    AxmeClient client = new AxmeClient(new AxmeClientConfig("https://gateway.example.com", "YOUR_API_KEY"));

    Map<String, Object> registered =
        client.registerNick(
            Map.of("nick", "@partner.user", "display_name", "Partner User"),
            new RequestOptions("nick-register-001", null));

    Map<String, Object> check = client.checkNick("@partner.user", RequestOptions.none());

    Map<String, Object> renamed =
        client.renameNick(
            Map.of("owner_agent", registered.get("owner_agent"), "nick", "@partner.new"),
            new RequestOptions("nick-rename-001", null));

    Map<String, Object> profile =
        client.getUserProfile((String) registered.get("owner_agent"), RequestOptions.none());

    Map<String, Object> updated =
        client.updateUserProfile(
            Map.of("owner_agent", profile.get("owner_agent"), "display_name", "Partner User Updated"),
            new RequestOptions("profile-update-001", null));

    System.out.println(check);
    System.out.println(renamed);
    System.out.println(updated);
  }
}
```

## Development

```bash
mvn test
```
