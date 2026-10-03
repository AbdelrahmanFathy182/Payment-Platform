Absolutely — here's a practical PostgreSQL privileges cheat sheet covering **what each privilege means, how to check it, and how to grant/revoke it**.

 ## PostgreSQL Privileges Cheat Sheet

 ### 1\. Database

 | Privilege | Meaning |
| --- | --- |
| `CONNECT` | Can connect to the database |
| `CREATE` | Can create schemas |
| `TEMPORARY` | Can create temporary tables |

**Check:**

```
\l
```

 or:

```
SELECT has_database_privilege('ledger_service', 'ledger_db', 'CONNECT');
```

 **Manage:**

```
GRANT CONNECT ON DATABASE ledger_db TO ledger_service;
REVOKE CONNECT ON DATABASE ledger_db FROM ledger_service;
```

---

 ### 2\. Schema

 | Privilege | Meaning |
| --- | --- |
| `USAGE` | Can access objects in the schema |
| `CREATE` | Can create objects in the schema |

**Check:**

```
\dn+
```

 or:

```
SELECT has_schema_privilege('ledger_service', 'public', 'USAGE');
```

 **Manage:**

```
GRANT USAGE ON SCHEMA public TO ledger_service;
GRANT CREATE ON SCHEMA public TO ledger_service;

REVOKE CREATE ON SCHEMA public FROM ledger_service;
```

---

 ### 3\. Table

 | Privilege | Meaning |
| --- | --- |
| `SELECT` | Read rows |
| `INSERT` | Add rows |
| `UPDATE` | Modify rows |
| `DELETE` | Delete rows |
| `TRUNCATE` | Empty table |
| `REFERENCES` | Use table in foreign keys |
| `TRIGGER` | Create triggers |

**Check:**

```
\dp
```

 or for a specific table:

```
SELECT has_table_privilege('ledger_service', 'public.entries', 'SELECT');
```

 **Manage:**

```
GRANT SELECT ON public.entries TO ledger_service;

GRANT SELECT, INSERT, UPDATE
ON public.entries
TO ledger_service;

REVOKE DELETE ON public.entries FROM ledger_service;
```

---

 ### 4\. Sequence

 Useful when using `SERIAL`, sequences, etc.

 | Privilege | Meaning |
| --- | --- |
| `USAGE` | Use the sequence |
| `SELECT` | Read sequence value |
| `UPDATE` | Modify sequence value |

**Check:**

```
\dp
```

 or:

```
SELECT has_sequence_privilege(
    'ledger_service',
    'public.entries_id_seq',
    'USAGE'
);
```

 **Manage:**

```
GRANT USAGE, SELECT
ON SEQUENCE public.entries_id_seq
TO ledger_service;

REVOKE UPDATE
ON SEQUENCE public.entries_id_seq
FROM ledger_service;
```

 For all sequences in a schema:

```
GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA public
TO ledger_service;
```

---

 ### 5\. Function

 | Privilege | Meaning |
| --- | --- |
| `EXECUTE` | Can call the function |

**Check:**

```
\df+
```

 or:

```
SELECT has_function_privilege(
    'ledger_service',
    'public.check_transaction_balanced()',
    'EXECUTE'
);
```

 **Manage:**

```
GRANT EXECUTE
ON FUNCTION public.check_transaction_balanced()
TO ledger_service;

REVOKE EXECUTE
ON FUNCTION public.check_transaction_balanced()
FROM ledger_service;
```

---

 ## Useful `psql` commands

 These are worth memorizing:

```
\l       → databases + database privileges
\dn+     → schemas + schema privileges
\dp      → tables/sequences + privileges
\df+     → functions + privileges
\du      → roles/users + role attributes
```

 ## The general pattern

 **Check:**

```
has_<object>_privilege(...)
```

 **Grant:**

```
GRANT <privilege>
ON <object>
TO <role>;
```

 **Revoke:**

```
REVOKE <privilege>
ON <object>
FROM <role>;
```

 For example:

```
GRANT SELECT, INSERT
ON public.entries
TO ledger_service;
```

 means:

 > `ledger_service` can read and insert into `entries`.

 While:

```
REVOKE DELETE
ON public.entries
FROM ledger_service;
```

 means:

 > `ledger_service` can no longer delete rows from `entries`.

 ### One useful distinction

 `GRANT` gives permissions **directly to a role**. PostgreSQL also lets roles inherit permissions from other roles:

```
ledger_service
      ↓ inherits
ledger_app
      ↓
SELECT / INSERT / UPDATE
```

 That's why `\du` is useful when you're trying to understand **where a permission came from**, rather than just whether a permission exists.