package org.example.restaurant.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanOrderDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldCascadeValidationToItemAmount() {
        ScanOrderDTO dto = dtoWithItems(List.of(item(1L, 0)));

        Set<ConstraintViolation<ScanOrderDTO>> violations = validator.validate(dto);

        assertTrue(hasViolationAt(violations, "items[0].amount"));
    }

    @Test
    void shouldRejectNullItem() {
        List<ScanOrderDTO.Item> items = new ArrayList<>();
        items.add(null);
        ScanOrderDTO dto = dtoWithItems(items);

        Set<ConstraintViolation<ScanOrderDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream()
                .anyMatch(v -> "菜品项不能为空".equals(v.getMessage())));
    }

    @Test
    void shouldRejectMoreThanFiftyDishKinds() {
        List<ScanOrderDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            items.add(item((long) i + 1, 1));
        }

        Set<ConstraintViolation<ScanOrderDTO>> violations = validator.validate(dtoWithItems(items));

        assertTrue(hasViolationAt(violations, "items"));
    }

    @Test
    void shouldAcceptQuantityBoundaries() {
        Set<ConstraintViolation<ScanOrderDTO>> minimum = validator.validate(
                dtoWithItems(List.of(item(1L, 1))));
        Set<ConstraintViolation<ScanOrderDTO>> maximum = validator.validate(
                dtoWithItems(List.of(item(1L, 99))));

        assertFalse(hasViolationAt(minimum, "items[0].amount"));
        assertFalse(hasViolationAt(maximum, "items[0].amount"));
    }

    @Test
    void shouldRejectQuantityAboveMaximum() {
        Set<ConstraintViolation<ScanOrderDTO>> violations = validator.validate(
                dtoWithItems(List.of(item(1L, 100))));

        assertTrue(hasViolationAt(violations, "items[0].amount"));
    }

    private boolean hasViolationAt(Set<ConstraintViolation<ScanOrderDTO>> violations, String path) {
        return violations.stream().anyMatch(v -> path.equals(v.getPropertyPath().toString()));
    }

    private ScanOrderDTO dtoWithItems(List<ScanOrderDTO.Item> items) {
        ScanOrderDTO dto = new ScanOrderDTO();
        dto.setTableId(1L);
        dto.setItems(items);
        return dto;
    }

    private ScanOrderDTO.Item item(Long dishId, Integer amount) {
        ScanOrderDTO.Item item = new ScanOrderDTO.Item();
        item.setDishId(dishId);
        item.setAmount(amount);
        return item;
    }
}
