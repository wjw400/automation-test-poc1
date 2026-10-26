package com.example.poc.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ProfileKeyMigrationTest {
    @Test
    void preservesEachKeyAndPassphraseUnderItsActualPurpose() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:profile-key-migration;MODE=PostgreSQL", "sa", "");
        // Keep the database alive while the migration obtains its own connection.
        source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                create table platform_profile (
                    id varchar(64) primary key,
                    sender_public_key text,
                    sender_private_key text,
                    sender_private_key_passphrase varchar(500),
                    recipient_public_key text,
                    recipient_private_key text,
                    recipient_private_key_passphrase varchar(500)
                )
                """);
        jdbc.update("insert into platform_profile values (?, ?, ?, ?, ?, ?, ?)",
                "profile", "their-verification-key", "our-signing-key", "signing-passphrase",
                "their-encryption-key", "our-decryption-key", "decryption-passphrase");

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V28__name_profile_keys_by_purpose.sql")).execute(source);

        assertThat(jdbc.queryForMap("select * from platform_profile where id = 'profile'"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "id", "profile",
                        "verification_public_key", "their-verification-key",
                        "signing_private_key", "our-signing-key",
                        "signing_private_key_passphrase", "signing-passphrase",
                        "encryption_public_key", "their-encryption-key",
                        "decryption_private_key", "our-decryption-key",
                        "decryption_private_key_passphrase", "decryption-passphrase"));
    }
}
