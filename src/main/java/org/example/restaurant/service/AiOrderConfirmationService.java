package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;

public interface AiOrderConfirmationService {
    AiOrderConfirmResponse confirm(AiOrderConfirmRequest request);
}
