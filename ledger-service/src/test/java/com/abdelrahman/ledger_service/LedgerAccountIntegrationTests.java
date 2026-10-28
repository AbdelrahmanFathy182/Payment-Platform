package com.abdelrahman.ledger_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * AccountIntegrationTests
 */
@AutoConfigureMockMvc
public class LedgerAccountIntegrationTests extends LedgerDatabaseSetup {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void health() throws Exception {
                mockMvc.perform(get("/api/v1/health"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        void shouldCreateAccount() throws Exception {
                mockMvc.perform(post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                "name": "test_account",
                                                "type": "asset",
                                                "currency": "EGP"
                                                }
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("test_account"));
        }

        @Test
        void shouldGetAccount() throws Exception {
                mockMvc.perform(get("/api/v1/accounts/bank_cash"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.type").value("asset"));
        }

        @Test
        void shouldUpdateAccountStatus() throws Exception {
                UUID testAccount = createAccount("liability");

                mockMvc.perform(patch("/api/v1/accounts/{name}", "test:" + testAccount)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                "active":false
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("inactive"));
        }

        @Test
        void shouldGetBalance() throws Exception {
                UUID testAccount1 = createAccount("asset");
                UUID testAccount2 = createAccount("liability");

                UUID txUuid = UUID.randomUUID();
                tx.executeWithoutResult(s -> insertPayment(txUuid, testAccount1, testAccount2, 100, "PAYMENT"));

                mockMvc.perform(get("/api/v1/accounts/{id}/balance", testAccount1))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountId").value(testAccount1.toString()))
                                .andExpect(jsonPath("$.balance").value(100))
                                .andExpect(jsonPath("$.currency").value("EGP"));

        }

        @Test
        void shouldGetEntryHistory() throws Exception {
                UUID testAccount1 = createAccount("asset");
                UUID testAccount2 = createAccount("liability");

                UUID txUuid1 = UUID.randomUUID();
                UUID txUuid2 = UUID.randomUUID();
                UUID txUuid3 = UUID.randomUUID();

                tx.executeWithoutResult(s -> insertPayment(txUuid1, testAccount1, testAccount2, 100, "PAYMENT"));
                tx.executeWithoutResult(s -> insertPayment(txUuid2, testAccount1, testAccount2, 200, "PAYMENT"));
                tx.executeWithoutResult(s -> insertPayment(txUuid3, testAccount2, testAccount1, 50, "PAYMENT"));

                mockMvc.perform(get("/api/v1/accounts/{id}/entries", testAccount1))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(3))
                                .andExpect(jsonPath("$[0].accountId").value(testAccount1.toString()))
                                .andExpect(jsonPath("$[1].accountId").value(testAccount1.toString()))
                                .andExpect(jsonPath("$[2].accountId").value(testAccount1.toString()));
        }
}