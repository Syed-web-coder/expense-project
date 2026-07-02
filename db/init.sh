psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /migrations/V1__schema.sql
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /migrations/V2__seed.sql || true
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /migrations/V3__outbox.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /migrations/V4__transaction_idempotency_key.sql
