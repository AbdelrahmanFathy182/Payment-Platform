package com.abdelrahman.ledger_service.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.abdelrahman.ledger_service.domain.Account;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.Collection;


public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByName(String name);
    List<Account> findAllByIdIn(Collection<UUID> ids);
    Optional<Account> findById(UUID accountId);
    

}