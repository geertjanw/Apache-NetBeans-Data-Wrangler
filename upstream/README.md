# Upstream change for `apache/netbeans` — `ide/db.sql.editor`

This directory holds the part that **cannot** live in a plugin: making NetBeans'
built-in SQL lexer and completion consult a vendor dialect. Once merged, the
`SqlDialect` implementation in the DuckDB module (`DuckDBDialect`) is picked up
automatically and the module's own `HighlightsLayer` keyword colouring becomes
redundant (it can then be deleted).

## Files to add

```
ide/db.sql.editor/src/org/netbeans/modules/db/sql/editor/spi/SqlDialect.java
ide/db.sql.editor/src/org/netbeans/modules/db/sql/editor/api/dialect/SqlDialectRegistry.java
```

Add both packages to `ide/db.sql.editor/nbproject/project.properties`:

```
friend.packages=... (unchanged)
public.packages=org.netbeans.modules.db.sql.editor.api.dialect,\
                org.netbeans.modules.db.sql.editor.spi
```

and bump `spec.version.base` (e.g. `1.60.0` → `1.61.0`).

## Edits to existing files (described, not diffed — line numbers drift)

### `src/org/netbeans/modules/db/sql/lexer/SQLLexer.java`

In the identifier branch, where the lexer decides between `SQLTokenId.KEYWORD`
and `SQLTokenId.IDENTIFIER` by calling `SQLKeywords.isSQL99ReservedKeyword(...)`
/ `isSQL99NonReservedKeyword(...)`, add a third check:

```java
} else if (SqlDialectRegistry.unionKeywords().contains(text.toString().toUpperCase(Locale.ROOT))
        || SqlDialectRegistry.unionTypes().contains(text.toString().toUpperCase(Locale.ROOT))) {
    return SQLTokenId.KEYWORD;
}
```

Also extend the operator scan so multi-character operators from
`SqlDialect.extraOperators()` (`->>`, `**`, `^@`, ...) lex as a single
`OPERATOR` token instead of a sequence of unknown characters. The simplest
approach: before the generic single-char operator case, try to match the
longest registered operator at the current position.

### `src/org/netbeans/modules/db/sql/editor/completion/SQLCompletionQuery.java`

Where keyword items are added (`completeKeyword` / the `KEYWORDS` array),
append items from `SqlDialectRegistry.forConnection(dbconn)` when non-null.
This lets `QUALIFY`, `ASOF`, `EXCLUDE` appear with the same rendering as ANSI
keywords instead of coming from a second provider.

### `src/org/netbeans/modules/db/sql/editor/completion/SQLStatementAnalyzer.java`

The analyzer's `FROM`-clause detection assumes `SELECT ... FROM`. Add a branch
for `SqlDialect.supportsFromFirst()` so `FROM t SELECT ...` and a bare
`FROM t` are recognised as select statements (this also fixes column
completion for PostgreSQL-style `TABLE t`).

### `src/org/netbeans/modules/db/sql/editor/completion/SQLCompletionItems.java`

Use `SqlDialect.identifierQuote()` instead of the hard-coded quoting helper
when the dialect is known.

## Why the DuckDB module still ships its own analysis

Even with this SPI merged, NetBeans has no syntax *error* reporting for SQL at
all. The DuckDB module's `DuckDBDiagnostics` (EXPLAIN-based) is what provides
red underlines and unresolved-reference warnings; that part is DuckDB-specific
by design and stays in the plugin.

## Separate interop bug: NetBeans `instanceof PreparedStatement` vs DuckDB JDBC

`ide/db.dataview/src/org/netbeans/modules/db/dataview/output/SQLExecutionHelper.java`,
`executeSQLStatementForExtraction`, chooses between `execute()` and `execute(sql)` with
`stmt instanceof PreparedStatement`. DuckDB's `createStatement()` returns a
`DuckDBPreparedStatement` (one class implements both interfaces), so NetBeans calls the
no-arg `execute()` on a statement that was never given SQL and every query fails with
"Query to execute was not specified".

Fix in NetBeans: have `prepareSQLStatement` return the decision it already made —
e.g. a small holder `{Statement stmt; boolean prepared;}` where `prepared = sql.startsWith("{")` —
and branch on that boolean instead of `instanceof`. Same pattern at the second call site
near line 610.

Fix in duckdb-java (worth filing too): `createStatement()` could return a class that does not
implement `PreparedStatement`; several tools use the same `instanceof` heuristic.

Until either lands, this plugin registers `NbDuckDBDriver`, a delegating wrapper whose
connections return `Statement`-only proxies from `createStatement()`.
