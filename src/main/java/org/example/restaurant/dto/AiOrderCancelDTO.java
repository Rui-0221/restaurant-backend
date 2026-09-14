package org.example.restaurant.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record AiOrderCancelDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId) { }
