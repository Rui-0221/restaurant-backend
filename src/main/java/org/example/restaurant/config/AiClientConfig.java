package org.example.restaurant.config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.http.HttpClient;
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {
    @Bean
    public HttpClient deepSeekHttpClient(AiProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }
}
