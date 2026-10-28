package com.abdelrahman.ledger_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

/**
 * LedgeTransactionIntegrationTests
 */
@AutoConfigureMockMvc
public class LedgeTransactionIntegrationTests extends LedgerDatabaseSetup {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldPostTransaction() throws Exception {
    UUID testAccount1 = createAccount("asset");
    UUID testAccount2 = createAccount("liability");
        mockMvc.perform(post("/api/v1/Transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {
                            "idempotencyKey":"key2",
                            "type": "TOPUP",
                            "currency": "EGP",
                            "entries":[
                            {
                            "accountId":"%S",
                            "direction": "credit",
                            "amount": 100
                            },
                            {
                            "accountId":"%S",
                            "direction": "debit",
                            "amount": 100
                            }]
                            }
                        """.formatted(testAccount1,testAccount2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.type").value("TOPUP"));
    }

    @Test
    public void shouldReverse() throws Exception {
        UUID testAccount1 = createAccount("asset");
        UUID testAccount2 = createAccount("liability");

        UUID txUuid = UUID.randomUUID();
        tx.executeWithoutResult(s -> insertPayment(txUuid, testAccount1, testAccount2, 100, "PAYMENT"));
     
        mockMvc.perform(post("/api/v1/Transactions/{id}/reversal",txUuid)
        .header("Idempotency-Key", "key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("EGP"))
        .andExpect(jsonPath("$.type").value("REVERSAL"));
    }

    @Test
    public void shouldGetTransaction() throws Exception {
        UUID testAccount1 = createAccount("asset");
        UUID testAccount2 = createAccount("liability");

        UUID txUuid = UUID.randomUUID();
        tx.executeWithoutResult(s -> insertPayment(txUuid, testAccount1, testAccount2, 100, "PAYMENT"));
     
        mockMvc.perform(get("/api/v1/Transactions/{id}",txUuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(txUuid.toString()))
        .andExpect(jsonPath("$.currency").value("EGP"))
        .andExpect(jsonPath("$.type").value("PAYMENT"));
    }

}