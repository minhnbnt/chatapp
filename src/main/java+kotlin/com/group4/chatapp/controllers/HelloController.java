package com.group4.chatapp.controllers;

import com.group4.chatapp.dtos.GreetingDto;
import com.group4.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cung cấp endpoint chào hỏi đơn giản cho ứng dụng.
 *
 * Endpoint này trả về lời chào mặc định hoặc lời chào cá nhân hóa theo
 * người dùng hiện tại lấy từ ngữ cảnh xác thực.
 */
@RestController
@RequiredArgsConstructor
public class HelloController {

    private final UserService userService;

    /**
     * Trả về lời chào cho người dùng hiện tại.
     *
     * Nếu hệ thống xác định được người dùng trong ngữ cảnh request, nội dung
     * trả về sẽ chứa username của người dùng đó. Nếu không, controller trả về
     * lời chào mặc định.
     *
     * @return DTO chứa chuỗi lời chào phù hợp với trạng thái xác thực hiện tại.
     */
    @GetMapping("/api/v1/hello/")
    public GreetingDto greeting() {

        var user = userService.getUserByContext();

        String message = "Hello, world!";
        if (user.isPresent()) {
            String username = user.get().getUsername();
            message = String.format("Hello, your username is %s.", username);
        }

        return new GreetingDto(message);
    }
}
