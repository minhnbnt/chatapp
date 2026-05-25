package com.group4.chatapp.configs

import com.group4.chatapp.repositories.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource

/**
 * Cấu hình bảo mật cho API HTTP của ứng dụng.
 *
 * Class này thiết lập cơ chế xác thực bằng Basic Auth và JWT, cấu hình CORS,
 * và khai báo các route nào yêu cầu đăng nhập trước khi truy cập.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig
@Autowired constructor(

    val jwtDecoder: JwtDecoder,
    val userRepository: UserRepository

) {

    /**
     * Tải người dùng theo username phục vụ xác thực bằng database.
     *
     * Behavior của method:
     * - Tìm user theo username trong repository.
     * - Nếu không tồn tại, ném `UsernameNotFoundException`.
     * - Dùng làm nguồn dữ liệu cho `DaoAuthenticationProvider`.
     *
     * @param username Tên đăng nhập cần tra cứu.
     * @return Người dùng tương ứng với username.
     */
    private fun loadByUsername(username: String) =
        userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found") }

    /**
     * Tạo security filter chain cho toàn bộ HTTP request.
     *
     * Behavior của method:
     * - Bật Basic Auth và JWT resource server.
     * - Tắt CSRF cho API.
     * - Cho phép CORS với mọi method và origin pattern `*`.
     * - Bảo vệ các endpoint nhạy cảm như messages, chatbot, invitations,
     *   profile cá nhân, chặn người dùng, đổi mật khẩu, và speech-to-text.
     * - Các request còn lại được phép truy cập công khai.
     *
     * @param http Đối tượng cấu hình `HttpSecurity` của Spring Security.
     * @return `SecurityFilterChain` đã được xây dựng.
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {

        http {

            httpBasic { }
            oauth2ResourceServer { jwt { jwtDecoder = jwtDecoder } }

            csrf { disable() }

            cors {
                configurationSource = CorsConfigurationSource {
                    CorsConfiguration().apply {

                        HttpMethod.values()
                            .forEach { method -> addAllowedMethod(method) }

                        allowCredentials = true
                        addAllowedOriginPattern("*")

                        applyPermitDefaultValues()
                    }
                }
            }

            authorizeHttpRequests {
                authorize("/api/v1/messages/**", authenticated)
                authorize(HttpMethod.POST, "/api/v1/speech-to-text", authenticated)
                authorize(HttpMethod.POST, "/speech-to-text", authenticated)
                authorize("/api/v1/chatbot/**", authenticated)
                authorize("/api/v1/invitations/**", authenticated)
                authorize("/api/v1/users/me/**", authenticated)
                authorize("/api/v1/users/blocks/**", authenticated)
                authorize(HttpMethod.POST, "/api/v1/users/*/block/", authenticated)
                authorize(HttpMethod.DELETE, "/api/v1/users/*/block/", authenticated)
                authorize(HttpMethod.GET, "/api/v1/users/*/block-status/", authenticated)
                authorize(HttpMethod.GET, "/api/v1/users/*/presence/", authenticated)
                authorize(HttpMethod.POST, "/api/v1/users/password/change/", authenticated)
                authorize(anyRequest, permitAll)
            }
        }

        return http.build()
    }

    /**
     * Tạo password encoder cho mật khẩu người dùng.
     *
     * @return `Argon2PasswordEncoder` theo cấu hình mặc định của Spring Security.
     */
    @Bean
    fun passwordEncoder() = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    /**
     * Tạo provider xác thực dựa trên username/password từ database.
     *
     * @param passwordEncoder Bộ mã hóa mật khẩu dùng để so khớp password.
     * @return `DaoAuthenticationProvider` được gắn nguồn user và password encoder.
     */
    @Bean
    fun daoAuthenticationProvider(passwordEncoder: PasswordEncoder) =
        DaoAuthenticationProvider(this::loadByUsername)
            .apply { setPasswordEncoder(passwordEncoder) }

    /**
     * Tạo provider xác thực JWT cho resource server.
     *
     * @return `JwtAuthenticationProvider` dùng `jwtDecoder` đã cấu hình.
     */
    @Bean
    fun jwtAuthenticationProvider() = JwtAuthenticationProvider(jwtDecoder)

    /**
     * Gộp các authentication provider thành authentication manager.
     *
     * @param providers Danh sách provider được Spring inject vào.
     * @return `ProviderManager` dùng để xử lý xác thực.
     */
    @Bean
    fun authenticationManager(providers: List<AuthenticationProvider>) =
        ProviderManager(providers)
}