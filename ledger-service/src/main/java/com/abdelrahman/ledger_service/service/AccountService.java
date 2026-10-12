package com.abdelrahman.ledger_service.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.util.Optional;
import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.accountEnums.AccountStatus;
import com.abdelrahman.ledger_service.dto.CreateAccountRequest;
import com.abdelrahman.ledger_service.exception.LedgerException;
import com.abdelrahman.ledger_service.repository.AccountRepository;

@Service
public class AccountService {

    /**
     * The account, plus whether this call created it (true) or it already existed
     * (false).
     */
    public record Result(Account account, boolean created) {
    }

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    // Deliberately NOT @Transactional. See the note below.
    public Result create(CreateAccountRequest request) {
        Optional<Account> found = repository.findByName(request.name());

        if (found.isPresent()) {
            return sameAs(found.get(), request); // name exists: compare
        }
        return insert(request); // name is new: create
    }

    private Result insert(CreateAccountRequest request) {
        Account account = new Account(UUID.randomUUID(), request.name(),
                AccountStatus.active, request.type(), request.currency());
        try {
            return new Result(repository.saveAndFlush(account), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a race: another request created this name first.
            Account existing = repository.findByName(request.name()).orElseThrow(() -> e);
            return sameAs(existing, request);
        }
    }

    private Result sameAs(Account existing, CreateAccountRequest request) {
        if (existing.getType() != request.type() || existing.getCurrency() != request.currency()) {
            throw new LedgerException("ACCOUNT_NAME_CONFLICT", 409,
                    "Account name already exists with a different type or currency");
        }
        return new Result(existing, false);
    }
}