package com.abdelrahman.ledger_service;

import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.TransactionType;
import com.abdelrahman.ledger_service.dto.Direction;
import com.abdelrahman.ledger_service.dto.EntryDTO;
import com.abdelrahman.ledger_service.dto.TransactionRequest;
import com.abdelrahman.ledger_service.exception.LedgerException;
import com.abdelrahman.ledger_service.service.TransactionService;

/**
 * LedgerTransactionServiceTests
 */
public class LedgerTransactionServiceTests extends LedgerDatabaseSetup {

    @Autowired
    private TransactionService service;

    @Test
    void transactionCreatedSuccess() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");
        UUID feesId = jdbcTemplate.queryForObject("SELECT  id FROM accounts WHERE name = ?", UUID.class,
                "revenue:fees");

        EntryDTO entryRequest1 = new EntryDTO(testBuyerId, Direction.credit, new BigDecimal(20));
        EntryDTO entryRequest2 = new EntryDTO(testSellerId, Direction.debit, new BigDecimal(15));
        EntryDTO entryRequest3 = new EntryDTO(feesId, Direction.debit, new BigDecimal(5));

        List<EntryDTO> entries = new ArrayList<>();
        entries.add(entryRequest3);
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request = new TransactionRequest("testkey1", TransactionType.PAYMENT, Currency.EGP, entries);
        TransactionService.CreateResult result = service.createTransaction(request);

        assertTrue(result.created());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, result.transaction().getId()));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class, result.transaction().getId()));

    }

    @Test
    void transactionWithUnbalancedEntries() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");

        EntryDTO entryRequest1 = new EntryDTO(testBuyerId, Direction.credit, BigDecimal.TWO);
        EntryDTO entryRequest2 = new EntryDTO(testSellerId, Direction.debit, BigDecimal.TEN);

        List<EntryDTO> entries = new ArrayList<>();
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request = new TransactionRequest("testkey2", TransactionType.PAYMENT, Currency.EGP, entries);
        assertThrows(LedgerException.class, () -> service.createTransaction(request));
    }

    @Test
    void transactionWithMissingAcccount() {
        UUID testSellerId = createAccount("liability");

        EntryDTO entryRequest1 = new EntryDTO(UUID.randomUUID(), Direction.credit, BigDecimal.TWO);
        EntryDTO entryRequest2 = new EntryDTO(testSellerId, Direction.debit, BigDecimal.TEN);

        List<EntryDTO> entries = new ArrayList<>();
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request = new TransactionRequest("testkey3", TransactionType.PAYMENT, Currency.EGP, entries);
        assertThrows(LedgerException.class, () -> service.createTransaction(request));
    }

    @Test
    void transactionWithDuplicateAcccount() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");

        EntryDTO entryRequest1 = new EntryDTO(testBuyerId, Direction.credit, new BigDecimal(5));
        EntryDTO entryRequest2 = new EntryDTO(testBuyerId, Direction.credit, new BigDecimal(5));
        EntryDTO entryRequest3 = new EntryDTO(testSellerId, Direction.debit, BigDecimal.TEN);

        List<EntryDTO> entries = new ArrayList<>();
        entries.add(entryRequest3);
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request = new TransactionRequest("testkey4", TransactionType.PAYMENT, Currency.EGP, entries);
        assertThrows(LedgerException.class, () -> service.createTransaction(request));
    }

    @Test
    void transactionWithInactiveAcccount() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");
        ownerJdbc().update("UPDATE accounts SET status = 'inactive' WHERE id = ?", testBuyerId);

        EntryDTO entryRequest1 = new EntryDTO(testBuyerId, Direction.credit, BigDecimal.TEN);
        EntryDTO entryRequest2 = new EntryDTO(testSellerId, Direction.debit, BigDecimal.TEN);

        List<EntryDTO> entries = new ArrayList<>();
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request = new TransactionRequest("testkey5", TransactionType.PAYMENT, Currency.EGP, entries);
        assertThrows(LedgerException.class, () -> service.createTransaction(request));
    }

    @Test
    void transactionWithSameIdempotencyKeyButDifferentHash() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");

        EntryDTO entryRequest1 = new EntryDTO(testBuyerId, Direction.credit, BigDecimal.TEN);
        EntryDTO entryRequest2 = new EntryDTO(testSellerId, Direction.debit, BigDecimal.TEN);

        List<EntryDTO> entries1 = new ArrayList<>();
        entries1.add(entryRequest2);
        entries1.add(entryRequest1);
        TransactionRequest request1 = new TransactionRequest("duplicatekey", TransactionType.PAYMENT, Currency.EGP,
                entries1);
        service.createTransaction(request1);

        EntryDTO entryRequest3 = new EntryDTO(testBuyerId, Direction.credit, BigDecimal.ONE);
        EntryDTO entryRequest4 = new EntryDTO(testSellerId, Direction.debit, BigDecimal.ONE);

        List<EntryDTO> entries2 = new ArrayList<>();
        entries2.add(entryRequest3);
        entries2.add(entryRequest4);
        TransactionRequest request2 = new TransactionRequest("duplicatekey", TransactionType.PAYMENT, Currency.EGP,
                entries2);

        assertThrows(LedgerException.class, () -> service.createTransaction(request2));
    }

    @Test
    void transactionWithSameIdempotencyKeyAndSamePayloadReturnsExistingTransaction() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");
        List<EntryDTO> entries = List.of(
                new EntryDTO(testSellerId, Direction.debit, BigDecimal.TEN),
                new EntryDTO(testBuyerId, Direction.credit, BigDecimal.TEN));
        TransactionRequest request = new TransactionRequest(
                "same-payload-key", TransactionType.PAYMENT, Currency.EGP, entries);

        TransactionService.CreateResult firstResult = service.createTransaction(request);
        TransactionService.CreateResult retryResult = service.createTransaction(request);

        assertTrue(firstResult.created());
        assertFalse(retryResult.created());
        assertEquals(firstResult.transaction().getId(), retryResult.transaction().getId());
    }

    @Test
    void reverseTransactionSuccess() {
        UUID testBuyerId = createAccount("liability");
        UUID testSellerId = createAccount("liability");
        UUID feesId = jdbcTemplate.queryForObject("SELECT  id FROM accounts WHERE name = ?", UUID.class,
                "revenue:fees");

        EntryDTO entryRequest1 = new EntryDTO(testBuyerId, Direction.credit, new BigDecimal(20));
        EntryDTO entryRequest2 = new EntryDTO(testSellerId, Direction.debit, new BigDecimal(15));
        EntryDTO entryRequest3 = new EntryDTO(feesId, Direction.debit, new BigDecimal(5));

        List<EntryDTO> entries = new ArrayList<>();
        entries.add(entryRequest3);
        entries.add(entryRequest2);
        entries.add(entryRequest1);
        TransactionRequest request = new TransactionRequest("testkey6", TransactionType.PAYMENT, Currency.EGP, entries);
        TransactionService.CreateResult result = service.createTransaction(request);

        TransactionService.ReversalResult reverseResult = service.reverseTransaction(result.transaction().getId(),
                "testkey7");
        assertTrue(reverseResult.reversed());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, result.transaction().getId()));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, reverseResult.transaction().getId()));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class, result.transaction().getId()));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class,
                reverseResult.transaction().getId()));

    }

}