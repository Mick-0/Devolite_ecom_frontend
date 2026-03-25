package com.verso.ai_client_form.service.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.verso.ai_client_form.repository.StaffUserRepository;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:Admin User}")
    private String adminFullName;

    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;

    public AdminUserInitializer(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            StaffUserRepository staffUserRepository) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin user not created: APP_ADMIN_PASSWORD is empty.");
            return;
        }
        Optional<String> existing = jdbc.query(
            "select username from auth.users where username = :u",
            new MapSqlParameterSource("u", adminUsername),
            rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty()
        );
        if (existing.isPresent()) {
            log.info("Admin user already exists: {}", adminUsername);
            return;
        }

        String encoded = passwordEncoder.encode(adminPassword);
        jdbc.update(
            "insert into auth.users (username, password, enabled) values (:u, :p, true)",
            Map.of("u", adminUsername, "p", encoded)
        );
        jdbc.update(
            "insert into auth.authorities (username, authority) values (:u, :a)",
            Map.of("u", adminUsername, "a", "ROLE_ADMIN")
        );
        jdbc.update(
            "insert into auth.authorities (username, authority) values (:u, :a)",
            Map.of("u", adminUsername, "a", "ROLE_SALES")
        );

        UUID staffId = staffUserRepository.upsertStaffUser(
            adminFullName,
            adminEmail,
            "sales",
            null
        );
        jdbc.update(
            "insert into auth.user_profile (username, staff_user_id) values (:u, :sid)",
            Map.of("u", adminUsername, "sid", staffId)
        );
        log.info("Admin user created: {}", adminUsername);
    }
}



