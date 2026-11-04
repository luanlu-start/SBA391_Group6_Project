package com.fptpost.modules.identity;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.datasource.url=jdbc:h2:mem:userdirectory;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.repositories.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserDirectoryLookup.class)
class UserDirectoryRepositoryTest {
    @Autowired EntityManager entities;
    @Autowired UserDirectoryLookup lookup;

    @Test void nameEmailAndUuidSearchIsBoundedAndKeepsLockedDeletedAndMissingProfiles() {
        User deleted = user("legacy@example.invalid", UserStatus.DELETED, true, "Alpha Legacy");
        User locked = user("locked@example.invalid", UserStatus.LOCKED, false, "Beta Locked");
        User noProfile = user("unprofiled@example.invalid", UserStatus.ACTIVE, false, null);
        entities.flush(); entities.clear();
        assertThat(lookup.search("", 2)).extracting(entry -> entry.publicId()).containsExactly(deleted.getPublicId(), locked.getPublicId());
        assertThat(lookup.search("LEGACY", 51)).singleElement().satisfies(entry -> {
            assertThat(entry.fullName()).isEqualTo("Alpha Legacy");
            assertThat(entry.email()).isEqualTo("legacy@example.invalid");
        });
        assertThat(lookup.search(locked.getPublicId().toString(), 51)).singleElement()
                .satisfies(entry -> assertThat(entry.publicId()).isEqualTo(locked.getPublicId()));
        assertThat(lookup.search("unprofiled@example.invalid", 51)).singleElement().satisfies(entry -> {
            assertThat(entry.publicId()).isEqualTo(noProfile.getPublicId());
            assertThat(entry.fullName()).isNull();
        });
    }

    @Test void sqlSearchTreatsWildcardAndEscapeCharactersLiterally() {
        user("literal@example.invalid", UserStatus.ACTIVE, false, "Literal %_[x]!");
        user("other@example.invalid", UserStatus.ACTIVE, false, "Other user");
        entities.flush(); entities.clear();
        assertThat(lookup.search("%_[x]!", 51)).singleElement()
                .satisfies(entry -> assertThat(entry.email()).isEqualTo("literal@example.invalid"));
        assertThat(lookup.search("%unmatched", 51)).isEmpty();
    }

    private User user(String email, UserStatus status, boolean deleted, String name) {
        Instant now = Instant.now();
        User user = User.builder().publicId(UUID.randomUUID()).email(email)
                .phone("09" + (10000000 + Math.abs(email.hashCode() % 89999999)))
                .passwordHash("synthetic-private-password-hash").status(status).deleted(deleted)
                .createdAt(now).updatedAt(now).build();
        entities.persist(user);
        if (name != null) entities.persist(UserProfile.builder().user(user).fullName(name).adultConfirmedAt(now).build());
        return user;
    }
}
