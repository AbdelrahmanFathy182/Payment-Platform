package com.abdelrahman.ledger_service.service;

import java.math.BigDecimal;
import java.util.List;

import com.abdelrahman.ledger_service.controller.AccountController;
import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Transaction;
import com.abdelrahman.ledger_service.domain.Enums.AccountStatus;
import com.abdelrahman.ledger_service.dto.Direction;
import com.abdelrahman.ledger_service.dto.EntryRequest;
import com.abdelrahman.ledger_service.dto.TransactionRequest;
import com.abdelrahman.ledger_service.exception.LedgerException;
import com.abdelrahman.ledger_service.repository.AccountRepository;
import com.abdelrahman.ledger_service.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository Trepository, AccountRepository Arepository) {
        this.accountRepository = Arepository;
        this.transactionRepository = Trepository;
    }

    public record CreateResult(Transaction transaction, Boolean created) {
    }

    @Transactional
    public CreateResult createTransaction(TransactionRequest request) {
        validateEntries(request.entries());

        if (insertTransaction(request.entries())) {
        }

        return null;
    }

    public void validateEntries(List<EntryRequest> entries) {

        BigDecimal creditsSum = BigDecimal.ZERO;
        BigDecimal debitsSum = BigDecimal.ZERO;
        for (EntryRequest entryRequest : entries) {
            if (entryRequest.direction() == Direction.credit) {
                creditsSum = creditsSum.add(entryRequest.amount());
            } else if (entryRequest.direction() == Direction.debit) {
                debitsSum = debitsSum.add(entryRequest.amount());
            }
        }
        if (creditsSum.compareTo(debitsSum) != 0) {
            throw new LedgerException("INVALID_REQUEST", 400, "total credits not equal total debits");
        }

        List<UUID> accountIds = entries.stream()
                .map(EntryRequest::accountId)
                .distinct()
                .toList();
        List<Account> accounts = accountRepository.findAllByIdIn(accountIds);
        boolean hasInactiveAccount = accounts.stream()
                .anyMatch(account -> account.getStatus() == AccountStatus.inactive);

        if (accounts.size() != accountIds.size()) {
            throw new LedgerException(
                    "ACCOUNT_NOT_FOUND",
                    404,
                    "one or more accounts do not exist");
        }
        if (hasInactiveAccount) {
            throw new LedgerException("CONFLICT", 409, "one or more accounts are inactive");

        }
    }

    public Boolean insertTransaction(List<EntryRequest> entries) {
        return null;
    }
}