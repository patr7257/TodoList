package dk.dtu.api.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DataSourcesTest {

    @Test
    void splitsInlineNeonCredentialsOutOfTheUrl() {
        String jdbc = "jdbc:postgresql://neondb_owner:npg_secret123@ep-late-surf.eu-central-1.aws.neon.tech/neondb?sslmode=require";
        DataSources.Parsed p = DataSources.parse(jdbc);
        assertEquals(
                "jdbc:postgresql://ep-late-surf.eu-central-1.aws.neon.tech/neondb?sslmode=require",
                p.url());
        assertEquals("neondb_owner", p.username());
        assertEquals("npg_secret123", p.password());
    }

    @Test
    void keepsExplicitPortAndDecodesEncodedCredentials() {
        String jdbc = "jdbc:postgresql://user%40acme:p%40ss%3Aword@db.example.com:5433/app";
        DataSources.Parsed p = DataSources.parse(jdbc);
        assertEquals("jdbc:postgresql://db.example.com:5433/app", p.url());
        assertEquals("user@acme", p.username());
        assertEquals("p@ss:word", p.password());
    }

    @Test
    void leavesCredentialFreeUrlUnchanged() {
        String jdbc = "jdbc:postgresql://db.example.com/app?sslmode=require";
        DataSources.Parsed p = DataSources.parse(jdbc);
        assertEquals(jdbc, p.url());
        assertNull(p.username());
        assertNull(p.password());
    }

    @Test
    void handlesNullAndNonJdbcInput() {
        assertNull(DataSources.parse(null).url());
        assertEquals("nonsense", DataSources.parse("nonsense").url());
    }
}
