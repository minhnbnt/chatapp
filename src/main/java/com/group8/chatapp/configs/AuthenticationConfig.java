package com.group8.chatapp.configs;

import com.group8.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

@Configuration
@RequiredArgsConstructor
public class AuthenticationConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private UserDetails getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    return new UsernameNotFoundException("User not found");
                });
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {

        var authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(this::getUserByUsername);
        authProvider.setPasswordEncoder(passwordEncoder);

        return authProvider;
    }

    @Bean
    public WebAuthenticationDetailsSource authenticationDetailsSource() {
        return new WebAuthenticationDetailsSource();
    }
}
