package com.abdelrahman.ledger_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
    void shouldGetAccount()throws Exception{
        mockMvc.perform(get("/api/v1/accounts/bank_cash"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("asset"));
    }
}