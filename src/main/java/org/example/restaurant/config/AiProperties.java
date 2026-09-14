package org.example.restaurant.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@Getter
@Setter
@ConfigurationProperties(prefix = "restaurant.ai")
public class AiProperties {
    private boolean enabled;
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-v4-flash";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(15);
    private int maxTokens = 2048;
}
