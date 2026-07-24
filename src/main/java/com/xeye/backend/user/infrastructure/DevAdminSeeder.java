package com.xeye.backend.user.infrastructure;

import com.xeye.backend.user.application.port.out.PasswordHasher;
import com.xeye.backend.user.application.port.out.UserRepository;
import com.xeye.backend.user.domain.model.Permission;
import com.xeye.backend.user.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Crea un admin al arrancar si no existe; solo en el perfil {@code dev}. */
@Component
@Profile("dev")
public class DevAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final String email;
    private final String password;

    public DevAdminSeeder(UserRepository users, PasswordHasher passwordHasher,
                          @Value("${xeye.dev.admin-email:admin@xeye.local}") String email,
                          @Value("${xeye.dev.admin-password:admin1234}") String password) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        users.save(new User(null, "Admin", "XEYE", email,
                passwordHasher.hash(password), Permission.ADMIN, null, null));
        log.info("Seeded dev admin user '{}' (password '{}')", email, password);
    }
}
