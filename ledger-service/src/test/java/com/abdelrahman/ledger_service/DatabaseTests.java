package com.abdelrahman.ledger_service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

public class DatabaseTests extends DatabaseSetup {

        @Test
        public void seedAccountsExist() {
                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM accounts WHERE id = ? AND name = 'bank_cash' AND type = 'asset' AND status = 'active'",
                                Integer.class, UUID.fromString("00000000-0000-0000-0000-000000000001")));
                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM accounts WHERE id = ? AND name = 'revenue:fees' AND type = 'revenue' AND status = 'active'",
                                Integer.class, UUID.fromString("00000000-0000-0000-0000-000000000002")));
        }

        @Test
        public void testValidTransactionInserted() {
                UUID accountA_id = createAccount("liability");
                UUID accountB_id = createAccount("liability");
                UUID transaction_id = UUID.randomUUID();
                tx.executeWithoutResult(status -> {
                        jdbcTemplate.update(
                                        "insert  into transactions (id, reverses_transaction_id, type,currency,idempotency_key) values (?,?,?,?,?)",
                                        transaction_id, null, "PAYMENT", "EGP", UUID.randomUUID().toString());
                        jdbcTemplate.update(
                                        "insert into entries (id, account_id, transaction_id, credits, debits, currency) values (?,?,?,?,?,?)",
                                        UUID.randomUUID(), accountA_id, transaction_id, 100, null, "EGP");
                        jdbcTemplate.update(
                                        "insert into entries (id, account_id, transaction_id, credits, debits, currency) values (?,?,?,?,?,?)",
                                        UUID.randomUUID(), accountB_id, transaction_id, null, 100, "EGP");

                });
                assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions where id = ?",
                                Integer.class,
                                transaction_id));
                assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM entries where transaction_id = ?",
                                Integer.class, transaction_id));
        }

        @Test
        public void testunbalancedTransactionInserted() {
                UUID accountA_id = createAccount("liability");
                UUID accountB_id = createAccount("liability");
                UUID transaction_id = UUID.randomUUID();
                Exception exception = assertThrows(Exception.class, () -> {
                        tx.executeWithoutResult(status -> {
                                jdbcTemplate.update(
                                                "insert  into transactions (id, reverses_transaction_id, type,currency,idempotency_key) values (?,?,?,?,?)",
                                                transaction_id, null, "PAYMENT", "EGP", UUID.randomUUID().toString());
                                jdbcTemplate.update(
                                                "insert into entries (id, account_id, transaction_id, credits, debits, currency) values (?,?,?,?,?,?)",
                                                UUID.randomUUID(), accountA_id, transaction_id, 200, null, "EGP");
                                jdbcTemplate.update(
                                                "insert into entries (id, account_id, transaction_id, credits, debits, currency) values (?,?,?,?,?,?)",
                                                UUID.randomUUID(), accountB_id, transaction_id, null, 100, "EGP");

                        });

                });
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, transaction_id));
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class,
                                transaction_id));

                System.out.println("Exception message: " + exception.getMessage());

        }

        @Test
        public void testTransactionWithoutEntriesIsRejected() {
                UUID transaction_id = UUID.randomUUID();
                Exception exception = assertThrows(Exception.class, () -> {
                        tx.executeWithoutResult(status -> {
                                jdbcTemplate.update(
                                                "insert  into transactions (id, reverses_transaction_id, type,currency,idempotency_key) values (?,?,?,?,?)",
                                                transaction_id, null, "PAYMENT", "EGP", UUID.randomUUID().toString());
                        });

                });
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, transaction_id));
                System.out.println("Exception message: " + exception.getMessage());

        }

        @Test
        public void statusCanBeUpdated() {
                UUID id = UUID.randomUUID();
                jdbcTemplate.update(
                                "INSERT INTO accounts (id, name, type, currency, status) VALUES (?, ?, ?, ?, ?)",
                                id, "test:status-" + id, "liability", "EGP", "active");

                jdbcTemplate.update("UPDATE accounts SET status = ? WHERE id = ?", "inactive", id);

                assertEquals("inactive", jdbcTemplate.queryForObject(
                                "SELECT status FROM accounts WHERE id = ?", String.class, id));
        }

        @Test
        public void appUserCannotChangeAccountName() {
                UUID id = createAccount("asset");
                Exception exception = assertThrows(Exception.class, () -> {
                        jdbcTemplate.update("UPDATE accounts SET name = ? WHERE id = ?", "new-name", id);
                });
                System.out.println("Exception message: " + exception.getMessage());
                assertEquals("test:" + id, jdbcTemplate.queryForObject(
                                "SELECT name FROM accounts WHERE id = ?", String.class, id));
        }

        @Test
        public void ownerConnectionWorks() {
                UUID id = createAccount("asset");
                ownerJdbc().update("UPDATE accounts SET status = 'inactive' WHERE id = ?", id);
                assertEquals("inactive", jdbcTemplate.queryForObject(
                                "SELECT status FROM accounts WHERE id = ?", String.class, id));
        }

        @Test
        public void triggerRejectsAccountNameChangeEvenForOwner() {
                JdbcTemplate owner = ownerJdbc();
                UUID id = createAccount("asset");

                assertThrows(Exception.class,
                                () -> owner.update("UPDATE accounts SET name = ? WHERE id = ?", "new-name", id));

                assertEquals("test:" + id, jdbcTemplate.queryForObject(
                                "SELECT name FROM accounts WHERE id = ?", String.class, id));
        }

        @Test
        public void entriesCannotBeAddedToCommittedTransaction() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // commit 2: try to add one more balanced pair to the now-sealed transaction
                assertThrows(Exception.class, () -> tx.executeWithoutResult(s -> {
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, ?, NULL, 'EGP')",
                                        UUID.randomUUID(), a, txId, 50);
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, NULL, ?, 'EGP')",
                                        UUID.randomUUID(), b, txId, 50);
                }));

                assertEquals(2, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class, txId));
        }

        @Test
        public void testEntriesCannotBeRemoved() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();
                JdbcTemplate owner = ownerJdbc();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // commit 2: try to remove all of the entries from the now-sealed transaction
                assertThrows(Exception.class, () -> owner.update(
                                "DELETE FROM entries WHERE transaction_id = ?",
                                txId));

                assertEquals(2, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class, txId));

        }

        @Test
        public void testEntriesCannotBeUpdated() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();
                JdbcTemplate owner = ownerJdbc();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // commit 2: try to update one of the entries from the now-sealed transaction
                assertThrows(Exception.class, () -> owner.update(
                                "UPDATE entries SET debits = debits  WHERE transaction_id = ? AND account_id = ?",
                                txId, a)

                );

                assertEquals(100L, jdbcTemplate.queryForObject(
                                "SELECT debits FROM entries WHERE transaction_id = ? AND account_id = ?", Long.class,
                                txId, a));

        }

        @Test
        public void testTransactionCannotBeUpdated() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();
                JdbcTemplate owner = ownerJdbc();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // commit 2: try to update the transaction from the now-sealed transaction
                assertThrows(Exception.class, () -> owner.update(
                                "UPDATE transactions SET type = ? WHERE id = ?",
                                "PAYMENT", txId));

                assertEquals("PAYMENT", jdbcTemplate.queryForObject(
                                "SELECT type FROM transactions WHERE id = ?", String.class,
                                txId));

        }

        @Test
        public void testTransactionCannotBeDeleted() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();
                JdbcTemplate owner = ownerJdbc();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // commit 2: try to delete the transaction from the now-sealed transaction
                assertThrows(Exception.class, () -> owner.update(
                                "DELETE FROM transactions WHERE id = ?",
                                txId));

                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class,
                                txId));

        }

        @Test
        public void testEntriesCannotBeTruncated() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();
                JdbcTemplate owner = ownerJdbc();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // entries: nothing references it, so only the trigger can stop this
                assertThrows(Exception.class, () -> owner.update("TRUNCATE TABLE entries"));

                assertEquals(2, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class,
                                txId));

        }

        @Test
        public void testTransactionCannotBeTruncated() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID txId = UUID.randomUUID();
                JdbcTemplate owner = ownerJdbc();

                // commit 1: a normal, valid payment
                tx.executeWithoutResult(s -> insertPayment(txId, a, b, 100, "PAYMENT"));

                // entries: nothing references it, so only the trigger can stop this
                assertThrows(Exception.class, () -> owner.update("TRUNCATE TABLE entries, transactions"));

                assertEquals(2, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class,
                                txId));

                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, txId));
        }

        @Test
        public void testAccountDeleteRejected() {
                UUID test = createAccount("liability");
                Exception e = assertThrows(Exception.class,
                                () -> jdbcTemplate.update("Delete from accounts where id =? ", test));
                System.out.print(e);
                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM accounts WHERE id = ?", Integer.class, test));

        }

        @Test
        public void testAccountDeleteByOwnerRejected() {
                UUID test = createAccount("liability");

                Exception e = assertThrows(Exception.class,
                                () -> ownerJdbc().update("Delete from accounts where id =? ", test));
                System.out.print(e);
                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM accounts WHERE id = ?", Integer.class, test));
        }

        @Test
        public void testAccountsCannotBeTruncated() {
                JdbcTemplate owner = ownerJdbc();
                Exception e = assertThrows(Exception.class, () -> owner.update("TRUNCATE TABLE entries, accounts"));
                System.out.print(e);
        }

        @Test
        public void testInactiveAccountCannotReceiveTransactions() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID tUuid = UUID.randomUUID();
                jdbcTemplate.queryForList(
                                "SELECT t.tgname, t.tgenabled, pg_get_functiondef(t.tgfoid) AS body " +
                                                "FROM pg_trigger t WHERE t.tgrelid = 'entries'::regclass AND NOT t.tgisinternal")
                                .forEach(System.out::println);
                jdbcTemplate.update("UPDATE accounts set status = 'inactive' where id = ? ", a);

                Exception e = assertThrows(Exception.class, () -> tx.executeWithoutResult(status -> {
                        insertPayment(tUuid, a, b, 100, "PAYMENT");
                }));
                System.out.print(e);
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, tUuid));

        }

        @Test
        public void testReverseTransaction() {
                UUID accountA = createAccount("liability");
                UUID accountB = createAccount("liability");
                UUID txn = UUID.randomUUID();
                UUID reversetxn = UUID.randomUUID();

                tx.executeWithoutResult(status -> {
                        insertPayment(txn, accountA, accountB, 100, "PAYMENT");
                });

                tx.executeWithoutResult(status -> {
                        insertReversal(reversetxn, accountB, accountA, 100, txn);
                });

                assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions where id = ?",
                                Integer.class, txn));

                assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions where id = ?",
                                Integer.class, reversetxn));

                assertEquals(txn, jdbcTemplate.queryForObject(
                                "SELECT reverses_transaction_id FROM transactions WHERE id = ?", UUID.class,
                                reversetxn));

                assertEquals("REVERSAL", jdbcTemplate.queryForObject(
                                "SELECT type FROM transactions WHERE id = ?", String.class, reversetxn));

        }

        @Test
        public void testReverseWith3Entries() {
                UUID accountA = createAccount("liability");
                UUID accountB = createAccount("liability");
                UUID txn = UUID.randomUUID();
                UUID reversetxn = UUID.randomUUID();

                tx.executeWithoutResult(status -> {
                        jdbcTemplate.update(
                                        "INSERT INTO transactions (id, type, currency, idempotency_key) VALUES (?, ?, 'EGP', ?)",
                                        txn, "PAYMENT", UUID.randomUUID().toString());
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, ?, NULL, 'EGP')",
                                        UUID.randomUUID(), accountA, txn, 200);
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, NULL, ?, 'EGP')",
                                        UUID.randomUUID(), accountB, txn, 100);
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, NULL, ?, 'EGP')",
                                        UUID.randomUUID(), accountB, txn, 100);
                });
                assertEquals(3, jdbcTemplate.queryForObject("Select COUNT(*) from entries where transaction_id = ?",
                                Integer.class, txn));

                tx.executeWithoutResult(status -> {
                        jdbcTemplate.update(
                                        "INSERT INTO transactions (id, type, currency, idempotency_key,reverses_transaction_id) VALUES (?, ?, 'EGP', ?,?)",
                                        reversetxn, "REVERSAL", UUID.randomUUID().toString(), txn);
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, NULL, ?, 'EGP')",
                                        UUID.randomUUID(), accountA, reversetxn, 200);
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, ?, NULL, 'EGP')",
                                        UUID.randomUUID(), accountB, reversetxn, 100);
                        jdbcTemplate.update(
                                        "INSERT INTO entries (id, account_id, transaction_id, debits, credits, currency) VALUES (?, ?, ?, ?, NULL, 'EGP')",
                                        UUID.randomUUID(), accountB, reversetxn, 100);
                });
                assertEquals(3, jdbcTemplate.queryForObject("Select COUNT(*) from entries where transaction_id = ?",
                                Integer.class, reversetxn));

        }

        @Test
        public void testReversalWithDiffAmount() {

                UUID accountA = createAccount("liability");
                UUID accountB = createAccount("liability");
                UUID txn = UUID.randomUUID();
                UUID reversetxn = UUID.randomUUID();

                tx.executeWithoutResult(status -> {
                        insertPayment(txn, accountA, accountB, 100, "PAYMENT");
                });

                Exception e = assertThrows(Exception.class, () -> {
                        tx.executeWithoutResult(status -> {
                                insertReversal(reversetxn, accountB, accountA, 200, txn);
                        });
                });
                System.out.print(e);
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, reversetxn));
        }

        @Test
        public void testReversalWithoutreversing() {

                UUID accountA = createAccount("liability");
                UUID accountB = createAccount("liability");
                UUID txn = UUID.randomUUID();
                UUID reversetxn = UUID.randomUUID();

                tx.executeWithoutResult(status -> {
                        insertPayment(txn, accountA, accountB, 100, "PAYMENT");
                });

                Exception e = assertThrows(Exception.class, () -> {
                        tx.executeWithoutResult(status -> {
                                insertReversal(reversetxn, accountA, accountB, 100, txn);
                        });
                });
                System.out.print(e);
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, reversetxn));
        }

        @Test
        public void testReversalWithWrongAccount() {

                UUID accountA = createAccount("liability");
                UUID accountB = createAccount("liability");
                UUID accountC = createAccount("liability");

                UUID txn = UUID.randomUUID();
                UUID reversetxn = UUID.randomUUID();

                tx.executeWithoutResult(status -> {
                        insertPayment(txn, accountA, accountB, 100, "PAYMENT");
                });

                Exception e = assertThrows(Exception.class, () -> {
                        tx.executeWithoutResult(status -> {
                                insertReversal(reversetxn, accountC, accountA, 100, txn);
                        });
                });
                System.out.print(e);
                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, reversetxn));
        }

        @Test
        public void reversalWithWrongRepeatCountsIsRejected() {
                UUID a = createAccount("liability");
                UUID b = createAccount("liability");
                UUID c = createAccount("liability");
                UUID txn = UUID.randomUUID();
                UUID reversetxn = UUID.randomUUID();

                // original: debit A, debit A, debit B, credit C x3
                tx.executeWithoutResult(s -> {
                        jdbcTemplate.update(
                                        "INSERT INTO transactions (id, type, currency, idempotency_key) VALUES (?, 'PAYMENT', 'EGP', ?)",
                                        txn, UUID.randomUUID().toString());
                        insertEntry(txn, a, "debits", 100);
                        insertEntry(txn, a, "debits", 100);
                        insertEntry(txn, b, "debits", 100);
                        insertEntry(txn, c, "credits", 100);
                        insertEntry(txn, c, "credits", 100);
                        insertEntry(txn, c, "credits", 100);
                });

                // bad reversal: credit A once, credit B twice, debit C x3 (balanced, same line
                // count)
                assertThrows(Exception.class, () -> tx.executeWithoutResult(s -> {
                        jdbcTemplate.update(
                                        "INSERT INTO transactions (id, type, currency, idempotency_key, reverses_transaction_id) VALUES (?, 'REVERSAL', 'EGP', ?, ?)",
                                        reversetxn, UUID.randomUUID().toString(), txn);
                        insertEntry(reversetxn, a, "credits", 100);
                        insertEntry(reversetxn, b, "credits", 100);
                        insertEntry(reversetxn, b, "credits", 100);
                        insertEntry(reversetxn, c, "debits", 100);
                        insertEntry(reversetxn, c, "debits", 100);
                        insertEntry(reversetxn, c, "debits", 100);
                }));

                assertEquals(0, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, reversetxn));
        }

}
