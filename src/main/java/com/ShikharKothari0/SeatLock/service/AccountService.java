package com.ShikharKothari0.SeatLock.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AccountService(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }
    public record Account(UUID id, String email, String displayName, String role) {}
    public static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    @Override public UserDetails loadUserByUsername(String email) {
        var rows = jdbc.queryForList("SELECT u.id,u.password_hash,u.role FROM app_user u JOIN account_email e ON e.user_id=u.id WHERE e.email=?", normalize(email));
        if (rows.isEmpty() || rows.getFirst().get("password_hash") == null) throw new UsernameNotFoundException("Invalid credentials");
        var row = rows.getFirst();
        return User.withUsername(row.get("id").toString()).password((String) row.get("password_hash"))
            .roles((String) row.get("role")).build();
    }

    @Transactional
    public void register(String email, String name, String password) {
        UUID id = UUID.randomUUID();
        String normalized = normalize(email);
        jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash) VALUES (?,?,?,?)", id, normalized, name.strip(), encoder.encode(password));
        jdbc.update("INSERT INTO account_email(email,user_id) VALUES (?,?)", normalized, id);
    }

    public Account account(String id) {
        return jdbc.queryForObject("SELECT id,email,display_name,role FROM app_user WHERE id=?", (rs, n) ->
            new Account(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"), rs.getString("role")), UUID.fromString(id));
    }
}
