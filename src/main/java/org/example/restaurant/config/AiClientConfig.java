package org.example.restaurant.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    @Bean
    @Qualifier("deepSeekRestClient")
    public RestClient deepSeekRestClient(AiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMillis(properties.getConnectTimeout().toMillis()));
        requestFactory.setReadTimeout(toMillis(properties.getReadTimeout().toMillis()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private int toMillis(long value) {
        return Math.toIntExact(Math.min(value, Integer.MAX_VALUE));
    }
}
