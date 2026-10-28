package com.abdelrahman.ledger_service.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Entry;
import com.abdelrahman.ledger_service.domain.Transaction;
import com.abdelrahman.ledger_service.domain.Enums.AccountStatus;
import com.abdelrahman.ledger_service.domain.Enums.Currency;
import com.abdelrahman.ledger_service.domain.Enums.TransactionType;
import com.abdelrahman.ledger_service.dto.Direction;
import com.abdelrahman.ledger_service.dto.EntryDTO;
import com.abdelrahman.ledger_service.dto.TransactionRequest;
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

    public record GetResult(Transaction transaction, Boolean found) {
    }

    public record ReversalResult(Transaction transaction, Boolean reversed) {
    }

    @Transactional
    public ReversalResult reverseTransaction(UUID TransactionId, String idempotencyKey) {
        Optional<Transaction> found = transactionRepository.findById(TransactionId);
        if (found.isEmpty()) {
            throw new LedgerException("TRANSACTION_NOT_FOUND", 404, "transaction id not found:" + TransactionId);
        }
        List<Entry> entries = entryRepository.findByTransaction(found.get());
        if(entries.isEmpty()){
            throw new LedgerException("ENTRIES_NOT_FOUND", 404, "no entries associated with this transaction found");
        }

        List<EntryDTO> reversedEntryRequests = entries.stream()
                .map(entry -> new EntryDTO(entry.getAccount().getId(),
                        entry.getDirection() == Direction.credit ? Direction.debit : Direction.credit,
                        entry.getAmount()))
                .toList();
        String idempotencyHash = hasher.sha256(buildTransactionString(
                TransactionType.REVERSAL, found.get().getCurrency(), reversedEntryRequests));

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transaction existingTransaction = existing.get();
            String existingHash = getIdempotencyHash(existingTransaction,
                    entryRepository.findByTransaction(existingTransaction));
            if (Objects.equals(idempotencyHash, existingHash)
                    && existingTransaction.getTransaction() != null
                    && TransactionId.equals(existingTransaction.getTransaction().getId())) {
                return new ReversalResult(existingTransaction, false);
            }
            throw new LedgerException("CONFLICT", 409,
                    "rejected request, same idempotency key but different hash");
        }

        Transaction reversedTransaction = found.get().getReverse(idempotencyKey, idempotencyHash);
        List<Entry> reversedEntries = new ArrayList<>();
        for (Entry entry : entries) {
            reversedEntries.add(entry.getReverse(reversedTransaction));
        }
        insertTransaction(reversedTransaction, reversedEntries);
        return new ReversalResult(reversedTransaction,true);
    }


    @Transactional
    public GetResult getTransaction(UUID id) {
        Optional<Transaction> found = transactionRepository.findById(id);
        if (found.isPresent()) {
            return new GetResult(found.get(), true);
        } else {
            throw new LedgerException("TRANSACTION_NOT_FOUND", 404, "transaction id not found:" + id);
        }
    }

    @Transactional
    public CreateResult createTransaction(TransactionRequest request) {
        List<Entry> entries = new ArrayList<>();
        Transaction transaction = buildTransaction(request, entries);

        Optional<Transaction> existing = findIdempotencyDuplicate(transaction);
        if (existing.isPresent()) {
            return new CreateResult(existing.get(), false);
        }

        validateEntries(request.entries());
        insertTransaction(transaction, entries);
        return new CreateResult(transaction, true);
    }

    public void validateEntries(List<EntryDTO> entries) {

        BigDecimal creditsSum = BigDecimal.ZERO;
        BigDecimal debitsSum = BigDecimal.ZERO;
        for (EntryDTO entryRequest : entries) {
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
                .map(EntryDTO::accountId)
                .distinct()
                .toList();
        List<Account> accounts = accountRepository.findAllByIdIn(accountIds);
        boolean hasInactiveAccount = accounts.stream()
                .anyMatch(account -> account.getStatus() == AccountStatus.inactive);

        boolean hasDuplicateAccount = entries.stream()
                .map(EntryDTO::accountId)
                .distinct()
                .count() != entries.size();
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

    private Optional<Transaction> findIdempotencyDuplicate(Transaction request) {
        Optional<Transaction> found = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (found.isPresent()) {
            List<Entry> foundEntries = entryRepository.findByTransaction(found.get());
            String requestHash = request.getIdempotencyHash();
            String foundHash = getIdempotencyHash(found.get(), foundEntries);

            if (Objects.equals(requestHash, foundHash)) {
                return found;
            } else {
                throw new LedgerException("CONFLICT", 409, "rejected request, same idempotency key but different hash");
            }
        }
        return Optional.empty();
    }

    public void insertTransaction(Transaction transaction, List<Entry> entries) {
        transactionRepository.insert(transaction);
        entryRepository.saveAll(entries);
    }

    private String getIdempotencyHash(Transaction transaction, List<Entry> entries) {
        if (transaction.getIdempotencyHash() != null) {
            return transaction.getIdempotencyHash();
        }
        List<EntryDTO> entryRequests = entries.stream()
                .map(entry -> new EntryDTO(entry.getAccount().getId(), entry.getDirection(), entry.getAmount()))
                .toList();
        return hasher.sha256(buildTransactionString(transaction.getType(), transaction.getCurrency(), entryRequests));
    }

    private String buildTransactionString(TransactionType type, Currency currency, List<EntryDTO> entries) {
        List<EntryDTO> sortedEntries = entries.stream()
                .sorted(Comparator.<EntryDTO, UUID>comparing(EntryDTO::accountId).thenComparing(EntryDTO::amount))
                .toList();
        StringBuilder TransactionString = new StringBuilder();

        TransactionString.append(type).append("|");
        TransactionString.append(currency).append("|");
        for (EntryDTO entry : sortedEntries) {
            TransactionString.append(entry.accountId()).append("|");
            TransactionString.append(entry.amount()).append("|");
            TransactionString.append(entry.direction()).append("|");
        }

        return TransactionString.toString();
    }

    public Transaction buildTransaction(TransactionRequest request, List<Entry> entriesrefrence) {
        String idempotencyHash = hasher.sha256(
                buildTransactionString(request.type(), request.currency(), request.entries()));
        Transaction transaction = new Transaction(UUID.randomUUID(), null, request.currency(), request.type(),
                request.idempotencyKey(), idempotencyHash);
        try {
            for (EntryDTO entryRequest : request.entries()) {
                Optional<Account> tempAccount = accountRepository.findById(entryRequest.accountId());
                if (entryRequest.direction() == Direction.credit) {
                    entriesrefrence
                            .add(new Entry(UUID.randomUUID(), tempAccount.get(), transaction, entryRequest.amount(),
                                    null,
                                    request.currency()));
                } else {
                    entriesrefrence
                            .add(new Entry(UUID.randomUUID(), tempAccount.get(), transaction, null,
                                    entryRequest.amount(),
                                    request.currency()));
                }
            }
            return transaction;
        } catch (Exception e) {
            throw new LedgerException("ACCOUNT_NOT_FOUND", 404, "one or more accounts do not exist");

        }
    }

}