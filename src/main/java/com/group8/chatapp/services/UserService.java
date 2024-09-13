package com.group8.chatapp.services;

import com.group8.chatapp.dtos.UserDto;
import com.group8.chatapp.models.User;
import com.group8.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationProvider authenticationProvider;

    private Optional<User>
    getUserByAuthentication(@Nullable Authentication authentication) {

        if (authentication == null) {
            return Optional.empty();
        }

        var principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return Optional.of(user);
        } else {
            return Optional.empty();
        }
    }

    public Optional<User> getUserByDto(UserDto dto) {

        var authentication = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(
                        dto.username(), dto.password()
                )
        );

        return getUserByAuthentication(authentication);
    }

    public Optional<User> getUserByContext() {

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        return getUserByAuthentication(authentication);
    }

    public void registerUser(UserDto dto) {

        var hashedPassword = passwordEncoder.encode(dto.password());

        var record = User.builder()
                .username(dto.username())
                .password(hashedPassword)
                .build();

        userRepository.save(record);
    }
}
