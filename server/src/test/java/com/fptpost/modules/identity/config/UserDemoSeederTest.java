package com.fptpost.modules.identity.config;

import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.RoleRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDemoSeeder Unit Tests")
class UserDemoSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should seed 4 sample accounts when none exist")
    void testSeedDemoUsers_WhenEmpty() {
        UserDemoSeeder seeder = new UserDemoSeeder(userRepository, userProfileRepository, roleRepository, passwordEncoder);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByCode(anyString())).thenReturn(Optional.of(Role.builder().code("SENDER").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .publicId(u.getPublicId())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .build();
        });

        seeder.seedDemoUsers();

        verify(userRepository, times(4)).saveAndFlush(any(User.class));
        verify(userProfileRepository, times(4)).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("Should skip seeding if users already exist (Idempotency)")
    void testSeedDemoUsers_WhenAlreadyExists() {
        UserDemoSeeder seeder = new UserDemoSeeder(userRepository, userProfileRepository, roleRepository, passwordEncoder);

        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        seeder.seedDemoUsers();

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userProfileRepository, never()).saveAndFlush(any(UserProfile.class));
    }
}
