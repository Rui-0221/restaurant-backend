package org.example.restaurant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "restaurant.ai-ordering.state")
public class AiOrderingStateProperties {
    private String keyPrefix = "restaurant:ai-order:";
    private Duration conversationTtl = Duration.ofMinutes(30);
    private Duration proposalTtl = Duration.ofMinutes(10);
    private Duration rateWindow = Duration.ofMinutes(1);
    private int maxRounds = 20;
    private int maxMessageLength = 500;
    private int maxConversationIdLength = 64;
    private int rateLimit = 10;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getConversationTtl() {
        return conversationTtl;
    }

    public void setConversationTtl(Duration conversationTtl) {
        this.conversationTtl = conversationTtl;
    }

    public Duration getProposalTtl() {
        return proposalTtl;
    }

    public void setProposalTtl(Duration proposalTtl) {
        this.proposalTtl = proposalTtl;
    }

    public Duration getRateWindow() {
        return rateWindow;
    }

    public void setRateWindow(Duration rateWindow) {
        this.rateWindow = rateWindow;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getMaxConversationIdLength() {
        return maxConversationIdLength;
    }

    public void setMaxConversationIdLength(int maxConversationIdLength) {
        this.maxConversationIdLength = maxConversationIdLength;
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(int rateLimit) {
        this.rateLimit = rateLimit;
    }
}
