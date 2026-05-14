package com.inventory.auth.security;

import com.inventory.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        // OAuth2 users have null password — use empty string so
                        // Spring Security doesn't NPE; they can never login via password form
                        user.getPasswordHash() != null ? user.getPasswordHash() : "",
                        user.isEnabled(),
                        true,  // accountNonExpired
                        true,  // credentialsNonExpired
                        true,  // accountNonLocked
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                ))
                .orElseThrow(() ->
                        new UsernameNotFoundException("No user found with email: " + email));
    }
}