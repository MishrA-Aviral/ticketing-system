package com.aviral.ticketingsystem.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused Bean Validation tests for BookingRequest.
 */
class BookingRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid request with valid parameters produces zero constraint violations")
    void validRequest_HasNoViolations() {
        BookingRequest request = new BookingRequest(1L, 2L, 4);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest(name = "Valid quantity {0} should pass validation")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @DisplayName("Valid quantity boundary checks (1 to 6) pass validation")
    void validQuantityBoundaries_HaveNoViolations(int quantity) {
        BookingRequest request = new BookingRequest(1L, 2L, quantity);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Quantity less than 1 produces constraint violation")
    void quantityLessThanOne_FailsValidation() {
        BookingRequest request = new BookingRequest(1L, 2L, 0);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("quantity");
    }

    @Test
    @DisplayName("Quantity greater than 6 produces constraint violation")
    void quantityGreaterThanSix_FailsValidation() {
        BookingRequest request = new BookingRequest(1L, 2L, 7);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("quantity");
    }

    @Test
    @DisplayName("Null fields produce constraint violations")
    void nullFields_FailValidation() {
        BookingRequest request = new BookingRequest(null, null, null);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(3);
    }

    @Test
    @DisplayName("Non-positive user ID and event ID produce constraint violations")
    void negativeOrZeroIds_FailValidation() {
        BookingRequest request = new BookingRequest(-1L, 0L, 2);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(2);
    }
}
