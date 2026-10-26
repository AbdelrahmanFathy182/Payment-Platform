package com.abdelrahman.ledger_service.repository;
import com.abdelrahman.ledger_service.domain.Account;
import com.abdelrahman.ledger_service.domain.Entry;
import java.util.UUID;
import org.springframework.data.repository.Repository;

import java.util.List;
import com.abdelrahman.ledger_service.domain.Transaction;

public interface EntryRepository extends  Repository<Entry,UUID>,EntryRepositoryCustom{

    List<Entry> findByTransaction(Transaction transaction);
    List<Entry> findByAccount(Account account);

}