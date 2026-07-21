package dk.dtu.api.db;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Runs the Flyway migrations against a DataSource.
 *
 * <p>Configured with {@code baselineOnMigrate=true} and {@code baselineVersion=1}
 * so it does NOT recreate the existing Neon tables: on a non-empty database with
 * no Flyway history (the real Neon), Flyway records version 1 as the baseline
 * (skipping V1's CREATE statements) and then applies only V2's additive columns.
 * On a fresh, empty database (embedded Postgres in tests) there is nothing to
 * baseline, so V1 creates the schema and V2 extends it.
 */
public final class Migrations {

    private Migrations() {
    }

    public static MigrateResult migrate(DataSource dataSource) {
        Flyway flyway = Flyway.configure(Migrations.class.getClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("baseline")
                .load();
        return flyway.migrate();
    }
}
