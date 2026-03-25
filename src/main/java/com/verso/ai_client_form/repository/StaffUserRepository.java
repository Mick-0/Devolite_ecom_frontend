package com.verso.ai_client_form.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffUserRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public StaffUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String sql = "select id from core.staff_user where email = :email";
        MapSqlParameterSource params = new MapSqlParameterSource("email", email);
        return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString("id"))) : Optional.empty());
    }

    public UUID upsertStaffUser(String fullName, String email, String role, String phone) {
        UUID id = findIdByEmail(email).orElse(UUID.randomUUID());
        String sql = """
            insert into core.staff_user (id, full_name, email, phone, role)
            values (:id, :full_name, :email, :phone, :role)
            on conflict (id) do update set
                full_name = excluded.full_name,
                email = excluded.email,
                phone = excluded.phone,
                role = excluded.role
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("full_name", fullName)
            .addValue("email", email)
            .addValue("phone", phone)
            .addValue("role", role);
        jdbc.update(sql, params);
        return id;
    }

    public Optional<UUID> findStaffIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = "select staff_user_id from auth.user_profile where username = :u";
        return jdbc.query(sql, new MapSqlParameterSource("u", username), rs ->
            rs.next() ? Optional.ofNullable(rs.getObject(1, UUID.class)) : Optional.empty()
        );
    }

    public void linkUserProfile(String username, UUID staffUserId) {
        if (username == null || username.isBlank() || staffUserId == null) {
            return;
        }
        String sql = "insert into auth.user_profile (username, staff_user_id) values (:u, :sid) on conflict (username) do update set staff_user_id = excluded.staff_user_id";
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("u", username)
            .addValue("sid", staffUserId)
        );
    }
}


