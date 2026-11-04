package com.fptpost.modules.identity.config;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:userseeder_it;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.auto-index-creation=false",
        "management.health.mail.enabled=false",
        "spring.profiles.active=test"
})
@DisplayName("UserDemoSeeder JPA Integration Test")
class UserDemoSeederIntegrationTest {

    @Autowired
    private UserDemoSeeder userDemoSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    @Transactional
    @DisplayName("Should successfully seed and persist users and profiles without Hibernate StaleObjectStateException")
    void testSeedUsersAndProfilesSuccessfully() {
        // Run seeder once
        userDemoSeeder.seedDemoUsers();

        Optional<Long> senderId = userRepository.findIdByIdentifier("sender@fptpost.vn");
        assertThat(senderId).isPresent();

        Optional<User> sender = userRepository.findById(senderId.get());
        assertThat(sender).isPresent();
        assertThat(sender.get().getRoles()).isNotEmpty();

        Optional<UserProfile> profile = userProfileRepository.findById(senderId.get());
        assertThat(profile).isPresent();
        assertThat(profile.get().getFullName()).isEqualTo("Nguyễn Văn Sender");

        // Run second time to verify idempotency (no StaleObjectStateException or unique violation)
        userDemoSeeder.seedDemoUsers();
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(4);
    }
}
