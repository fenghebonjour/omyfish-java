package com.omyfish.shop.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyfish.shop.adapter.in.web.dto.CheckoutRequest;
import com.omyfish.shop.config.FeatureFlags;
import com.omyfish.shop.domain.port.in.CheckoutUseCase;
import com.omyfish.shop.domain.port.in.ConfirmPaymentWebhookUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers the web-layer defenses added alongside the idempotency-key mechanism: the now-required
// Idempotency-Key header, bean-validated CheckoutRequest fields, and the feature-flag kill switch.
@WebMvcTest(CheckoutController.class)
@Import(FeatureFlags.class)
class CheckoutControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired FeatureFlags featureFlags;
    @MockBean CheckoutUseCase checkoutUseCase;
    @MockBean ConfirmPaymentWebhookUseCase confirmPaymentWebhookUseCase;

    @AfterEach
    void resetFeatureFlag() {
        featureFlags.setCheckoutEnabled(true);
    }

    @Test
    void checkout_happyPath_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(checkoutUseCase.checkout(any(), any(), any(), any(), any()))
            .thenReturn(new CheckoutUseCase.CheckoutResult(orderId, "https://stripe.test/sess_1", false));

        mvc.perform(post("/api/v1/shop/checkout")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CheckoutRequest("https://ok", "https://cancel", null))))
            .andExpect(status().isOk());
    }

    @Test
    void checkout_missingIdempotencyKeyHeader_returns400() throws Exception {
        mvc.perform(post("/api/v1/shop/checkout")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CheckoutRequest("https://ok", "https://cancel", null))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_blankSuccessUrl_returns400() throws Exception {
        mvc.perform(post("/api/v1/shop/checkout")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CheckoutRequest("", "https://cancel", null))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_featureFlagDisabled_returns503WithoutCallingUseCase() throws Exception {
        featureFlags.setCheckoutEnabled(false);

        mvc.perform(post("/api/v1/shop/checkout")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CheckoutRequest("https://ok", "https://cancel", null))))
            .andExpect(status().isServiceUnavailable());
    }
}
