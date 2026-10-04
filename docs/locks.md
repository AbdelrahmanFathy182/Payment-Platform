The locks you program are **row locks**, written as `SELECT ... FOR <strength>`. Advisory locks are the only other kind you can write yourself, and they're rare.

## Row locks you write

All three work the same way: you put a "do not disturb" sign on a row, and it stays until your transaction commits or rolls back. They differ in what they stop others from doing.

**`FOR UPDATE`**

The "nobody touches this row, in any way" sign. Others can't update it, delete it, or lock it. It also blocks the weak foreign-key lock, so inserting a row that points at it has to wait.

*Everyday example:* a seat on a flight. While you're booking it, nobody else can book it, change it, or even attach a meal request to it.

*Use it when* you're about to delete a row, or change its key or unique columns.

**`FOR NO KEY UPDATE`**

The same sign, but it still lets other tables point at the row. Others can't update or delete it, but a foreign-key reference can still be created.

*Everyday example:* the same seat, but the airline can still attach a meal request to it while you edit your booking.

*Use it when* you're locking a row so you can safely change its normal columns. This is the default choice for your payer lock, because CafeNile's incoming credits must not wait behind it.

**`FOR SHARE`**

A "read lock": many transactions can hold it at once, but nobody can update or delete the row while it's held.

*Everyday example:* several people reading the same notice on a wall, while nobody is allowed to take it down.

*Use it when* you only need the row to stay unchanged while you read it. Rare, and you won't need it for the ledger.

## Advisory locks

You lock a number you pick, like `10`, and it isn't tied to any row. Use them only for special cases, such as making sure one copy of a scheduled job runs at a time. Skip them for now.


