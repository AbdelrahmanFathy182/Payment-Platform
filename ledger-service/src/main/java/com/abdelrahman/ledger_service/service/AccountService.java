package com.abdelrahman.ledger_service.service;

import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.util.Optional;
import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Enums.AccountStatus;
import com.abdelrahman.ledger_service.dto.CreateAccountRequest;
import com.abdelrahman.ledger_service.dto.CreateStatusUpdateRequest;
import com.abdelrahman.ledger_service.exception.LedgerException;
import com.abdelrahman.ledger_service.repository.AccountRepository;

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

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    // Deliberately NOT @Transactional. See the note below.
    public CreateResult create(CreateAccountRequest request) {
        Optional<Account> found = repository.findByName(request.name());

        if (found.isPresent()) {
            return sameAs(found.get(), request); // name exists: compare
        }
        return insert(request); // name is new: create
    }

    private CreateResult insert(CreateAccountRequest request) {
        Account account = new Account(UUID.randomUUID(), request.name(),
                AccountStatus.active, request.type(), request.currency());
        try {
            return new CreateResult(repository.saveAndFlush(account), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a race: another request created this name first.
            Account existing = repository.findByName(request.name()).orElseThrow(() -> e);
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
    public  UpdateResult updateStatus(String name, CreateStatusUpdateRequest request) {
        Optional<Account> found = repository.findByName(name);
        if (found.isPresent()) {
            found.get().setStatus(request.active() ? AccountStatus.active : AccountStatus.inactive);
            return new UpdateResult(repository.save(found.get()), true);
        }
        throw new LedgerException("ACCOUNT_NAME_NOT_FOUND", 404, "Account not found:" + name);
    }
}