## JDBC cheat sheet

**Declarations**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import java.util.UUID;
```

**In a test class** (field injection, as in your `DatabaseSetup`):
```java
@Autowired protected JdbcTemplate jdbcTemplate;       // connects as ledger_service
@Autowired protected TransactionTemplate tx;          // one BEGIN ... COMMIT
```

**In app code** (constructor injection, no `@Autowired` needed):
```java
private final JdbcTemplate jdbc;
public MyClass(JdbcTemplate jdbc) { this.jdbc = jdbc; }
```

**A second connection as another user** (your `ownerJdbc()`):
```java
new JdbcTemplate(new DriverManagerDataSource(url, "flyway_user", "flyway_pass"));
```
It needs no Spring bean. Spring's transaction doesn't control it, so each statement commits by itself.

**Dependency** (`pom.xml`): `spring-boot-starter-jdbc`. It comes along automatically with the JPA starter.

**Usage**

| Job | Code |
|---|---|
| Insert, update, delete | `int rows = jdbcTemplate.update("UPDATE accounts SET status = ? WHERE id = ?", "inactive", id);` |
| One value | `Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM entries WHERE transaction_id = ?", Integer.class, txId);` |
| Many rows as maps | `List<Map<String,Object>> rows = jdbcTemplate.queryForList("SELECT * FROM accounts");` |
| Several statements, one commit | `tx.executeWithoutResult(status -> { ... });` |
| Expect a rejection | `assertThrows(Exception.class, () -> jdbcTemplate.update(...));` |

**Rules**
- Always use `?`, never `+`, to put values in SQL.
- Pass a `UUID` object for UUID columns and a `String` for text.
- Without `tx`, each statement commits on its own.
- A deferred trigger fails at commit, so the exception comes out of `tx.executeWithoutResult(...)`.
- `tx` is the `TransactionTemplate`. The lambda parameter (`status`) is a `TransactionStatus`, which you usually ignore.

