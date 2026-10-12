package com.abdelrahman.ledger_service.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.abdelrahman.ledger_service.domain.Account;
import java.util.UUID;
import java.util.Optional;


public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByName(String name);
}