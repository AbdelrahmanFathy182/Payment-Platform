package com.abdelrahman.psp_simulator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.abdelrahman.psp_simulator.domain.enums.Currency;
import com.abdelrahman.psp_simulator.domain.enums.PaymentState;
import com.abdelrahman.psp_simulator.dto.AuthorizeRequest;
import com.abdelrahman.psp_simulator.dto.PaymentResponse;
import com.abdelrahman.psp_simulator.exception.ApiExceptionHandler;
import com.abdelrahman.psp_simulator.exception.PspException;
import com.abdelrahman.psp_simulator.web.ApiHeaders;

import jakarta.validation.Valid;

public class ApiExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void pspExceptionMapsToStatusAndCode() throws Exception {
        mvc.perform(post("/boom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(PspException.PAYMENT_NOT_FOUND));
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mvc.perform(post("/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.MISSING_IDEMPOTENCY_KEY));
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        mvc.perform(post("/authorize")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/authorize")
                .header(ApiHeaders.IDEMPOTENCY_KEY, "K1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void invalidPathIdIsBadRequest() throws Exception {
        mvc.perform(get("/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PspException.INVALID_REQUEST));
    }

    @Test
    void paymentResponseKeepsContractShape() throws Exception {
        mvc.perform(get("/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.state").value("AUTHORIZED"))
                .andExpect(jsonPath("$.amount").value(15000))
                .andExpect(jsonPath("$.currency").value("EGP"));
    }

    @RestController
    static class DummyController {

        @PostMapping("/boom")
        void boom() {
            throw PspException.paymentNotFound();
        }

        @PostMapping("/authorize")
        void authorize(@RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String key,
                @Valid @RequestBody AuthorizeRequest request) {
        }

        @GetMapping("/payments/{id}")
        void payment(@PathVariable UUID id) {
        }

        @GetMapping("/payment")
        PaymentResponse payment() {
            return new PaymentResponse(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    PaymentState.AUTHORIZED,
                    15000,
                    Currency.EGP,
                    null);
        }
    }
}
