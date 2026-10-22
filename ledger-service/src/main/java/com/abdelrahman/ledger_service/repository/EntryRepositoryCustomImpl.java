package com.abdelrahman.ledger_service.repository;

import java.util.List;

import jakarta.persistence.EntityManager;
import com.abdelrahman.ledger_service.domain.Entry;

public class EntryRepositoryCustomImpl implements EntryRepositoryCustom {

    private final EntityManager manager;

    public EntryRepositoryCustomImpl(EntityManager manager) {
        this.manager = manager;
    }

    @Override
    public void saveAll(List<Entry> entries) {
        for (Entry entry : entries) {
            manager.persist(entry);
        }
    }

}