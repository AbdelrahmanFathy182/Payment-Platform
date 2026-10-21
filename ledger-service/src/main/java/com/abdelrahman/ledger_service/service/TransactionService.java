package com.abdelrahman.ledger_service.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Entry;
import com.abdelrahman.ledger_service.domain.Transaction;
import com.abdelrahman.ledger_service.domain.Enums.AccountStatus;
import com.abdelrahman.ledger_service.dto.Direction;
import com.abdelrahman.ledger_service.dto.EntryRequest;
import com.abdelrahman.ledger_service.dto.TransactionRequest;
import com.abdelrahman.ledger_service.dto.TransactionResponse;
import com.abdelrahman.ledger_service.exception.LedgerException;
import com.abdelrahman.ledger_service.repository.AccountRepository;
import com.abdelrahman.ledger_service.repository.EntryRepository;
import com.abdelrahman.ledger_service.repository.TransactionRepository;
import com.abdelrahman.ledger_service.util.Hasher;

import java.util.Optional;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final EntryRepository entryRepository;
    private final Hasher hasher;

    public TransactionService(TransactionRepository tRepository, AccountRepository aRepository,
            EntryRepository eRepository, Hasher hasher) {
        this.accountRepository = aRepository;
        this.transactionRepository = tRepository;
        this.entryRepository = eRepository;
        this.hasher = hasher;
    }

    public record CreateResult(Transaction transaction, Boolean created) {
    }

    @Transactional
    public CreateResult createTransaction(TransactionRequest request) {
        Transaction transaction = null;
        List<Entry> entries = null;
        transaction = buildTransaction(request, entries);
        if (!checkIdempotencyDuplicate(transaction)) {
            validateEntries(request.entries());
            insertTransaction(transaction, entries);
        }
        return new CreateResult(transaction, true);
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

        boolean hasDuplicateAccount = accounts.stream().map(Account::getId).distinct().count() != accounts.size();

        if (accounts.size() != accountIds.size()) {
            throw new LedgerException("ACCOUNT_NOT_FOUND", 404, "one or more accounts do not exist");
        }
        if (hasDuplicateAccount) {
            throw new LedgerException("CONFLICT", 409, "one or more accounts are duplicates");
        }
        if (hasInactiveAccount) {
            throw new LedgerException("CONFLICT", 409, "one or more accounts are inactive");

        }
    }

    public boolean checkIdempotencyDuplicate(Transaction request) {
        Optional<Transaction> found = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (found.isPresent()) {
            List<Entry> requestEntries = entryRepository.findByTransaction(request);
            List<Entry> foundEntries = entryRepository.findByTransaction(found.get());
            String requestString = buildTransactionString(request, requestEntries);
            String foundString = buildTransactionString(found.get(), foundEntries);
            String requestHash = hasher.sha256(requestString);
            String foundHash = hasher.sha256(foundString);

            if (requestHash == foundHash) {
                return true;
            } else {
                throw new LedgerException("CONFLICT", 409, "rejected request, same idempotency key but different hash");
            }
        }
        return false;
    }

    public TransactionResponse insertTransaction(Transaction transaction, List<Entry> entries) {
        return null;
    }

    private String buildTransactionString(Transaction transaction, List<Entry> entries) {
        List<Entry> sortedEntries = entries.stream()
                .sorted(Comparator.<Entry, UUID>comparing(e -> e.getAccount().getId()).thenComparing(Entry::getAmount))
                .toList();

        StringBuilder TransactionString = new StringBuilder();

        TransactionString.append(transaction.getType()).append("|");
        TransactionString.append(transaction.getCurrency()).append("|");
        for (Entry entry : sortedEntries) {
            TransactionString.append(entry.getAccount().getId()).append("|");
            TransactionString.append(entry.getAmount()).append("|");
            TransactionString.append(entry.getDirection()).append("|");
        }

        return TransactionString.toString();
    }

    public Transaction buildTransaction(TransactionRequest request, List<Entry> entriesrefrence) {
        Transaction transaction = new Transaction(UUID.randomUUID(), null, request.currency(), request.type(),
                request.idempotencyKey());
        entriesrefrence = new ArrayList<>();
        for (EntryRequest entryRequest : request.entries()) {
            Optional<Account> tempAccount = accountRepository.findById(entryRequest.accountId());
            if (entryRequest.direction() == Direction.credit) {
                entriesrefrence
                        .add(new Entry(UUID.randomUUID(), tempAccount.get(), transaction, entryRequest.amount(), null,
                                request.currency()));
            } else {
                entriesrefrence
                        .add(new Entry(UUID.randomUUID(), tempAccount.get(), transaction, null, entryRequest.amount(),
                                request.currency()));
            }
        }
        return transaction;
    }

}