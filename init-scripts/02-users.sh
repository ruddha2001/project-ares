#!/bin/bash
set -e

echo "🚀 [ARES-INIT] Initializing users schema and seeding root admin..."

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v root_admin="${ARES_ROOT_ADMIN:-ruddha2001}" <<-EOSQL
    
    CREATE TABLE IF NOT EXISTS ares_users (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        github_username VARCHAR(255) NOT NULL UNIQUE,
        is_admin BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

    INSERT INTO ares_users (github_username, is_admin)
    VALUES (:'root_admin', TRUE)
    ON CONFLICT (github_username) DO NOTHING;
EOSQL

echo "🏆 [ARES-INIT] Users table seeded successfully."