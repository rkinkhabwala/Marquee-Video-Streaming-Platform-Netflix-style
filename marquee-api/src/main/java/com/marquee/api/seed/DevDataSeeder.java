package com.marquee.api.seed;

import com.marquee.api.catalog.Genre;
import com.marquee.api.catalog.GenreRepository;
import com.marquee.api.profile.Profile;
import com.marquee.api.profile.ProfileRepository;
import com.marquee.api.user.Role;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local seed data (Phase 1): one admin, one user with an adult and a kids profile, and a
 * starter set of genres. Enabled with {@code SEED_ENABLED=true}; safe to run on every start.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    static final List<String> GENRES = List.of("Action", "Animation", "Comedy", "Documentary", "Drama", "Sci-Fi");

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final GenreRepository genreRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String userEmail;
    private final String userPassword;

    public DevDataSeeder(UserRepository userRepository,
                         ProfileRepository profileRepository,
                         GenreRepository genreRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.seed.admin-email}") String adminEmail,
                         @Value("${app.seed.admin-password}") String adminPassword,
                         @Value("${app.seed.user-email}") String userEmail,
                         @Value("${app.seed.user-password}") String userPassword) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.genreRepository = genreRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword.isBlank() || userPassword.isBlank()) {
            throw new IllegalStateException("SEED_ADMIN_PASSWORD and SEED_USER_PASSWORD must be set when SEED_ENABLED=true");
        }

        User admin = ensureUser(adminEmail, adminPassword, Role.ADMIN);
        ensureProfile(admin, "Admin", false);

        User viewer = ensureUser(userEmail, userPassword, Role.USER);
        ensureProfile(viewer, "Viewer", false);
        ensureProfile(viewer, "Kids", true);

        for (String name : GENRES) {
            if (genreRepository.findByNameIgnoreCase(name).isEmpty()) {
                genreRepository.save(new Genre(name));
            }
        }
        log.info("Seed data ready: admin {}, user {}, {} genres", adminEmail, userEmail, GENRES.size());
    }

    private User ensureUser(String email, String password, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User(email, passwordEncoder.encode(password));
            user.setRole(role);
            return userRepository.save(user);
        });
    }

    private void ensureProfile(User user, String name, boolean kids) {
        boolean exists = profileRepository.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .anyMatch(profile -> profile.getName().equals(name));
        if (!exists) {
            profileRepository.save(new Profile(user, name, null, kids));
        }
    }
}
