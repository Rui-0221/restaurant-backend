package org.example.restaurant.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@Getter
@Setter
@ConfigurationProperties(prefix = "restaurant.ai-ordering.state")
public class AiOrderingStateProperties {
    private String keyPrefix = "restaurant:ai-order:v2:";
    private Duration conversationTtl = Duration.ofMinutes(30);
    private Duration rateWindow = Duration.ofMinutes(1);
    private int maxRounds = 20;
    private int maxMessageLength = 500;
    private int rateLimit = 10;
}
