package com.omyfish.identity.adapter.in.web;

import com.omyfish.identity.application.service.BillingService;
import com.omyfish.identity.domain.exception.WebhookVerificationException;
import com.omyfish.identity.domain.port.out.PaymentPort;
import com.omyfish.identity.domain.port.out.TokenPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = BillingController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class BillingControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BillingService billing;
    @MockBean PaymentPort payments;
    @MockBean TokenPort tokenPort;

    @Test
    void webhook_unhandledEventType_isAcknowledged() throws Exception {
        when(payments.isConfigured()).thenReturn(true);
        when(payments.verifyWebhook(anyString(), anyString())).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "sig")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.handled").value(false));
    }

    @Test
    void webhook_invalidSignature_returns400() throws Exception {
        when(payments.isConfigured()).thenReturn(true);
        when(payments.verifyWebhook(anyString(), anyString()))
            .thenThrow(new WebhookVerificationException("Invalid Stripe webhook signature"));

        mvc.perform(post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "bad")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid Stripe webhook signature"));
    }

    @Test
    void webhook_unverifiableConfiguration_returns503() throws Exception {
        when(payments.isConfigured()).thenReturn(true);
        when(payments.verifyWebhook(anyString(), anyString()))
            .thenThrow(new IllegalStateException("webhook secret missing"));

        mvc.perform(post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "sig")
                .content("{}"))
            .andExpect(status().isServiceUnavailable());
    }
}
