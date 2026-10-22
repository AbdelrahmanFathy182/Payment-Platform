package com.abdelrahman.ledger_service.repository;
import com.abdelrahman.ledger_service.domain.Entry;
import java.util.UUID;
import org.springframework.data.repository.Repository;

import java.util.List;
import com.abdelrahman.ledger_service.domain.Transaction;
import java.util.Optional;

public interface EntryRepository extends  Repository<Entry,UUID>,EntryRepositoryCustom{

    Optional<List<Entry>> findByTransaction(Transaction transaction);
}