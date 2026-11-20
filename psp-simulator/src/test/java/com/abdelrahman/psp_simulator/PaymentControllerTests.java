package com.abdelrahman.psp_simulator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.abdelrahman.psp_simulator.dto.PaymentResponse;
import com.abdelrahman.psp_simulator.exception.PspException;
import com.abdelrahman.psp_simulator.web.ApiHeaders;

import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
public class PaymentControllerTests extends pspDatabaseSetup {

    private static final String BODY = "{\"amount\":15000,\"currency\":\"EGP\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void authorizeReturnsPayment() throws Exception {
        mvc.perform(post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.state").value("AUTHORIZED"))
                .andExpect(jsonPath("$.amount").value(15000))
                .andExpect(jsonPath("$.currency").value("EGP"));
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mvc.perform(post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.MISSING_IDEMPOTENCY_KEY));
    }

    @Test
    void blankIdempotencyKeyIsBadRequest() throws Exception {
        mvc.perform(post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, " ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.MISSING_IDEMPOTENCY_KEY));
    }

    @Test
    void invalidAmountIsBadRequest() throws Exception {
        mvc.perform(post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0,\"currency\":\"EGP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void declineScenarioReturnsDeclined() throws Exception {
        mvc.perform(post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .header(ApiHeaders.SIMULATE, "DECLINE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("SIMULATED_DECLINE"));
    }

    @Test
    void temporaryFailureScenarioReturns503() throws Exception {
        mvc.perform(post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .header(ApiHeaders.SIMULATE, "TEMP_FAILURE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(PspException.TEMPORARY_FAILURE));
    }

    @Test
    void unknownScenarioIsBadRequest() throws Exception {
        mvc.perform(post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .header(ApiHeaders.SIMULATE, "EXPLODE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void queryByIdReturnsCurrentState() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(get("/payments/{id}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.state").value("AUTHORIZED"));
    }

    @Test
    void queryByKeyReturnsCurrentState() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(get("/payments").param("key", "K1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.state").value("AUTHORIZED"));
    }

    @Test
    void queryByUnknownKeyIsNotFound() throws Exception {
        mvc.perform(get("/payments").param("key", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(PspException.PAYMENT_NOT_FOUND));
    }

    @Test
    void queryWithoutKeyParameterIsBadRequest() throws Exception {
        mvc.perform(get("/payments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void queryWithScenarioIsBadRequest() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(get("/payments/{id}", created.id())
                .header(ApiHeaders.SIMULATE, "DELAY:10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void unknownPaymentIsNotFound() throws Exception {
        mvc.perform(get("/payments/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(PspException.PAYMENT_NOT_FOUND));
    }

    @Test
    void malformedPaymentIdIsBadRequest() throws Exception {
        mvc.perform(get("/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void captureMovesPaymentToCaptured() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(post("/payments/{id}/capture", created.id())
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CAPTURED"));
    }

    @Test
    void voidMovesPaymentToVoided() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(post("/payments/{id}/void", created.id())
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VOIDED"));
    }

    @Test
    void secondCaptureWithNewKeyConflicts() throws Exception {
        PaymentResponse created = authorize("K1", null);
        capture(created.id(), "K2");

        mvc.perform(post("/payments/{id}/capture", created.id())
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_TRANSITION));
    }

    @Test
    void keyReusedWithDifferentOperationConflicts() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(post("/payments/{id}/capture", created.id())
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(PspException.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void captureWithoutKeyIsBadRequest() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(post("/payments/{id}/capture", created.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.MISSING_IDEMPOTENCY_KEY));
    }

    @Test
    void captureWithDeclineScenarioIsBadRequest() throws Exception {
        PaymentResponse created = authorize("K1", null);

        mvc.perform(post("/payments/{id}/capture", created.id())
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K2")
                .header(ApiHeaders.SIMULATE, "DECLINE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    private PaymentResponse authorize(String key, String simulate) throws Exception {
        MockHttpServletRequestBuilder request = post("/payments")
                .header(ApiHeaders.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
        if (simulate != null) {
            request.header(ApiHeaders.SIMULATE, simulate);
        }

        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readValue(body, PaymentResponse.class);
    }

    private void capture(java.util.UUID id, String key) throws Exception {
        mvc.perform(post("/payments/{id}/capture", id)
                .header(ApiHeaders.IDEMPOTENCY_KEY, key))
                .andExpect(status().isOk());
    }
}
