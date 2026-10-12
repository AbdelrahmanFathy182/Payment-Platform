package com.abdelrahman.ledger_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.abdelrahman.ledger_service.repository.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

class LedgerRepositoryTests extends LedgerDatabaseSetup {

    @Autowired 
    private  AccountRepository repository;

    @Test
    void accountFound() {
        UUID testAccountUuid = createAccount("liability");
        assertTrue(repository.findByName("test:" + testAccountUuid).isPresent());
    }

    @Test
    void accountNotFound() {
        assertFalse(repository.findByName("badAccount").isPresent());
    }
}
