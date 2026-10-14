package com.fptpost.modules.identity.config;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.RoleRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Automatically seeds sample users with role assignments and easy passwords for testing/dev environments.
 */
@Slf4j
@Component
@Order(1) // Runs immediately after IdentityDataInitializer (Order 0)
@Profile({"dev", "demo", "test", "local"})
@RequiredArgsConstructor
public class UserDemoSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public static final String DEFAULT_PASSWORD = "password123";
    public static final String SIMPLE_PASSWORD = "12345678";

    @Override
    @Transactional
    public void run(String... args) {
        seedDemoUsers();
    }

    /**
     * Seeds sample accounts for Sender, Courier, Admin, and Moderator.
     */
    @Transactional
    public void seedDemoUsers() {
        log.info("--> Initializing sample test users (Password: 12345678)...");

        seedUser("sender@fptpost.vn", "+84901234567", "Nguyễn Văn Sender", "SENDER");
        seedUser("courier@fptpost.vn", "+84902345678", "Trần Văn Shipper", "COURIER");
        seedUser("admin@fptpost.vn", "+84903456789", "Lê Quản Trị Admin", "ADMIN");
        seedUser("moderator@fptpost.vn", "+84904567890", "Hoàng Kiểm Duyệt", "MODERATOR");

        log.info("--> Sample test users initialized successfully!");
    }

    private void seedUser(String email, String phone, String fullName, String roleCode) {
        if (userRepository.existsByEmail(email) || userRepository.existsByPhone(phone)) {
            log.info("Demo user already exists (email: {}, phone: {}), skipping.", email, phone);
            return;
        }

        Role role = roleRepository.findByCode(roleCode).orElseGet(() -> {
            Role newRole = Role.builder().code(roleCode).build();
            return roleRepository.saveAndFlush(newRole);
        });

        Instant now = Instant.now();
        String passwordHash = passwordEncoder.encode(SIMPLE_PASSWORD);

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        User user = User.builder()
                .publicId(UUID.randomUUID())
                .email(email)
                .phone(phone)
                .passwordHash(passwordHash)
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(now)
                .phoneVerifiedAt(now)
                .fptBadge(true)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .roles(roles)
                .build();

        user = userRepository.saveAndFlush(user);

        if (!userProfileRepository.existsById(user.getId())) {
            UserProfile profile = UserProfile.builder()
                    .user(user)
                    .fullName(fullName)
                    .adultConfirmedAt(now)
                    .build();

            userProfileRepository.save(profile);
        }

        log.info("Created demo user: {} | Phone: {} | Role: {} | Password: {}", email, phone, roleCode, SIMPLE_PASSWORD);
    }
}
