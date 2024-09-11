package com.group8.chatapp.services;

import com.group8.chatapp.dtos.UserDto;
import com.group8.chatapp.models.User;
import com.group8.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<UserDetails> getUserByDto(UserDto dto) {

        var userRecord = userRepository.findByUsername(dto.username());

        if (userRecord.isEmpty()) {
            return Optional.empty();
        }

        var encodedPassword = userRecord.get().getPassword();
        if (passwordEncoder.matches(dto.password(), encodedPassword)) {
            return Optional.of(userRecord.get());
        } else {
            return Optional.empty();
        }
    }

    public Optional<UserDetails> getUserByContext() {

        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null) {
            return Optional.empty();
        }

        var principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return Optional.of(userDetails);
        } else {
            return Optional.empty();
        }
    }

    public void registerUser(String username, String password) {

        var hashedPassword = passwordEncoder.encode(password);

        var record = User.builder()
                .username(username)
                .password(hashedPassword)
                .build();

        userRepository.save(record);
    }
}
