package com.abdelrahman.ledger_service.service;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.abdelrahman.ledger_service.domain.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Enums.AccountStatus;
import com.abdelrahman.ledger_service.dto.CreateAccountRequest;
import com.abdelrahman.ledger_service.dto.CreateStatusUpdateRequest;
import com.abdelrahman.ledger_service.dto.Direction;
import com.abdelrahman.ledger_service.exception.LedgerException;
import com.abdelrahman.ledger_service.repository.AccountRepository;
import com.abdelrahman.ledger_service.repository.EntryRepository;
import java.math.BigDecimal;
import com.abdelrahman.ledger_service.domain.Enums.AccountType;
import com.abdelrahman.ledger_service.dto.EntryDTO;

@Service
public class AccountService {

    /**
     * The account, plus whether this call created it (true) or it already existed
     * (false).
     */
    public record CreateResult(Account account, boolean created) {
    }

    public record UpdateResult(Account account, boolean updated) {
    }

    public record GetResult(Account account, boolean found) {
    }

    public record BalanceResult(Account account, BigDecimal balance) {
    }

    public record EntryHistoryResult(List<EntryDTO> entries) {
    }

    private final AccountRepository accountRepository;
    private final EntryRepository entryRepository;

    public AccountService(AccountRepository aRepository, EntryRepository eRepository) {
        this.accountRepository = aRepository;
        this.entryRepository = eRepository;
    }

    // Deliberately NOT @Transactional. See the note below.
    public CreateResult create(CreateAccountRequest request) {
        Optional<Account> found = accountRepository.findByName(request.name());

        if (found.isPresent()) {
            return sameAs(found.get(), request); // name exists: compare
        }
        return insert(request); // name is new: create
    }

    private CreateResult insert(CreateAccountRequest request) {
        Account account = new Account(UUID.randomUUID(), request.name(),
                AccountStatus.active, request.type(), request.currency());
        try {
            return new CreateResult(accountRepository.saveAndFlush(account), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a race: another request created this name first.
            Account existing = accountRepository.findByName(request.name()).orElseThrow(() -> e);
            return sameAs(existing, request);
        }
    }

    private CreateResult sameAs(Account existing, CreateAccountRequest request) {
        if (existing.getType() != request.type() || existing.getCurrency() != request.currency()) {
            throw new LedgerException("ACCOUNT_NAME_CONFLICT", 409,
                    "Account name already exists with a different type or currency");
        }
        return new CreateResult(existing, false);
    }

    @Transactional
    public UpdateResult updateStatus(String name, CreateStatusUpdateRequest request) {
        Optional<Account> found = accountRepository.findByName(name);
        if (found.isPresent()) {
            found.get().setStatus(request.active() ? AccountStatus.active : AccountStatus.inactive);
            return new UpdateResult(accountRepository.save(found.get()), true);
        }
        throw new LedgerException("ACCOUNT_NAME_NOT_FOUND", 404, "Account not found:" + name);
    }

    @Transactional
    public GetResult getAccount(String name) {
        Optional<Account> found = accountRepository.findByName(name);

        if (found.isPresent()) {
            return new GetResult(found.get(), true);
        } else {
            throw new LedgerException("ACCOUNT_NAME_NOT_FOUND", 404, "Account not found:" + name);

        }

    }

    @Transactional
    public BalanceResult getBalance(UUID accountId) {
        Optional<Account> found = accountRepository.findById(accountId);
        if (found.isEmpty()) {
            throw new LedgerException("ACCOUNT_NOT_FOUND", 404, "Account not found with ID:" + accountId);
        }
        List<Entry> entries = entryRepository.findByAccount(found.get());
        if (entries.isEmpty()) {
            return new BalanceResult(found.get(), BigDecimal.ZERO);
        }
        BigDecimal sumCredits = new BigDecimal(0);
        BigDecimal sumDebits = new BigDecimal(0);

        for (Entry entry : entries) {
            if (entry.getDirection() == Direction.credit) {
                sumCredits = sumCredits.add(entry.getAmount());
            } else {
                sumDebits = sumDebits.add(entry.getAmount());
            }
        }

        // Accounttype Debit Credit
        // asset Increases Decreases
        // expense Increases Decreases
        // equity Decreases Increases
        // liability Decreases Increases
        // revenue Decreases Increases
        AccountType type = found.get().getType();

        if (type.equals(AccountType.liability) || type.equals(AccountType.equity) || type.equals(AccountType.revenue)) {
            return new BalanceResult(found.get(), sumCredits.subtract(sumDebits));
        } else if (type.equals(AccountType.asset) || type.equals(AccountType.expense)) {
            return new BalanceResult(found.get(), sumDebits.subtract(sumCredits));
        } else {
            throw new LedgerException("ACCOUNT_TYPE_WRONG", 400, "Account with ID:" + accountId + "has bad type");
        }
    }

    @Transactional
    public EntryHistoryResult getEntryHistory(UUID accountId) {
        Optional<Account> account = accountRepository.findById(accountId);
        if (account.isEmpty()) {
            throw new LedgerException("ACCOUNT_NOT_FOUND", 404, "Account not found with ID:" + accountId);
        }
        List<Entry> entries = entryRepository.findByAccount(account.get());
        if (entries.isEmpty()) {
            throw new LedgerException("ENTRIES_NOT_FOUND", 404, "Account with ID:" + accountId + "has no entries");
        }
        List<EntryDTO> entriesResponse = new ArrayList<>();
        for (Entry entry : entries) {
            entriesResponse.add(EntryDTO.from(entry));
        }
        return new EntryHistoryResult(entriesResponse);
    }
}