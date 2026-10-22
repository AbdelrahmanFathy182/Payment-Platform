package com.abdelrahman.ledger_service.repository;
import java.util.List;
import com.abdelrahman.ledger_service.domain.Entry;

public interface EntryRepositoryCustom {
        void saveAll(List<Entry> entries);
}