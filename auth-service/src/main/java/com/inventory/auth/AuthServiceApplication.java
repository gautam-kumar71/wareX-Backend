package com.inventory.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.inventory.auth.repository.UserRepository;
import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;

@SpringBootApplication
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "app.seed-default-users.enabled", havingValue = "true", matchIfMissing = true)
    public CommandLineRunner adminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed Admin
            seedUser(userRepository, passwordEncoder, "admin@warex.com", "System Administrator", Role.ADMIN);
            
            // Seed Inventory Manager
            seedUser(userRepository, passwordEncoder, "manager@warex.com", "Inventory Manager", Role.INVENTORY_MANAGER);
            
            // Seed Purchase Officer
            seedUser(userRepository, passwordEncoder, "purchase@warex.com", "Purchase Officer", Role.PURCHASE_OFFICER);
            
            // Seed Warehouse Staff
            seedUser(userRepository, passwordEncoder, "staff@warex.com", "Warehouse Staff", Role.WAREHOUSE_STAFF);
        };
    }

    private void seedUser(UserRepository repo, PasswordEncoder encoder, String email, String name, Role role) {
        repo.findByEmail(email).ifPresentOrElse(
            user -> {
                user.setPasswordHash(encoder.encode("Password@123"));
                user.setRole(role);
                user.setEnabled(true);
                repo.save(user);
            },
            () -> {
                User user = User.builder()
                    .email(email)
                    .passwordHash(encoder.encode("Password@123"))
                    .fullName(name)
                    .role(role)
                    .enabled(true)
                    .build();
                repo.save(user);
            }
        );
    }
}
