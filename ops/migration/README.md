# V44.3.2 Flyway migration activation

## What this package does

- `V1__baseline_live_schema.sql`: complete schema exported from the live PostgreSQL 18.4 database.
- `V2__master_data.sql`: current idempotent dictionary/category/master-data seed.
- `V3__japan_business_calendar.sql`: current JP business-calendar seed, with the psql-only `\set` removed.
- `application-migration.yml`: enables Flyway and changes Hibernate to `ddl-auto=validate` only when the `migration` profile is active.

The existing `application.yml` is intentionally not overwritten.

## Existing database — first activation

Before the first activation, create one normal logical backup.

Then add `migration` to the backend active profiles, for example:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: prod,migration
```

Restart only the backend.

For the current non-empty `bills` schema, Flyway uses `baseline-on-migrate=true` and records version 1
without executing V1 against the existing tables. It then runs V2 and V3. Both are intended to be
idempotent seeds.

After startup:

```sql
SELECT installed_rank, version, description, type, success
FROM bills.flyway_schema_history
ORDER BY installed_rank;
```

Expected history is a baseline row for version 1 followed by successful V2 and V3 migrations.

Hibernate then validates the resulting schema instead of changing it.

## Brand-new database

For a genuinely empty database, no baseline row is injected. Flyway executes V1, V2 and V3 in order,
then Hibernate validates the created schema.

## Rollback

Do not use Flyway `clean` on this finance database. It is disabled by configuration.

If first activation fails:
1. stop backend;
2. restore the PostgreSQL dump created immediately before activation;
3. remove `migration` from `SPRING_PROFILES_ACTIVE`;
4. start backend with the previous configuration.

## Old admin SQL

`db/admin/*.sql` remains in the repository as historical operational material, but after V44.3.2 it
must not be used for future schema evolution. New structural changes should be added as V4, V5, ...
Flyway migrations.
