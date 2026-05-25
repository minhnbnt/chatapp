package com.group4.chatapp.unit.service

import com.group4.chatapp.dtos.user.UserDto
import com.group4.chatapp.dtos.user.UserProfileUpdateDto
import com.group4.chatapp.exceptions.ApiException
import com.group4.chatapp.models.Attachment
import com.group4.chatapp.models.User
import com.group4.chatapp.repositories.AttachmentRepository
import com.group4.chatapp.repositories.UserRepository
import com.group4.chatapp.services.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.mail.MailSendException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.ErrorResponseException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class UserServiceEdgeCaseTest {

    @Mock lateinit var repository: UserRepository
    @Mock lateinit var attachmentRepository: AttachmentRepository
    @Mock lateinit var passwordEncoder: PasswordEncoder
    @Mock lateinit var s3Service: S3Service
    @Mock lateinit var fileTypeService: FileTypeService
    @Mock lateinit var messagingTemplate: SimpMessagingTemplate
    @Mock lateinit var userCacheService: UserCacheService
    @Mock lateinit var passwordResetTokenService: PasswordResetTokenService
    @Mock lateinit var emailService: EmailService

    @InjectMocks
    lateinit var userService: UserService

    private fun buildUser(
        id: Long = 1L,
        username: String = "testuser",
        displayName: String? = "Test User",
        password: String = "encoded_password"
    ): User {
        return User.builder()
            .id(id)
            .username(username)
            .password(password)
            .displayName(displayName)
            .build()
    }

    /** Simulates a cached user (as getCachedUser builds it — no password) */
    private fun buildCachedUser(id: Long = 1L, username: String = "testuser", displayName: String? = "Test User"): User {
        return User.builder()
            .id(id)
            .username(username)
            .displayName(displayName)
            // password intentionally omitted — this is what UserCacheService.getCachedUser returns
            .build()
    }

    private fun setSecurityContext(user: User) {
        val authentication = mock<Authentication> {
            on { principal } doReturn user
        }
        val securityContext = mock<SecurityContext> {
            on { getAuthentication() } doReturn authentication
        }
        SecurityContextHolder.setContext(securityContext)
    }

    // =========================================================================
    // updateCurrentProfile — edge cases
    // =========================================================================

    @Nested
    @DisplayName("updateCurrentProfile — edge cases")
    inner class UpdateCurrentProfileEdgeCases {

        @Test
        fun `should handle cached user without password (BUG-1 reproduction)`() {
            // CRITICAL: This test documents BUG-1.
            // When user is loaded from Redis cache, password is null.
            // Calling repository.save() with null password violates DB NOT NULL constraint.
            val cachedUser = buildCachedUser()
            setSecurityContext(cachedUser)

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn "New Name"
                on { avatar() } doReturn null
            }

            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            val savedUser = argumentCaptor<User>()

            userService.updateCurrentProfile(dto)

            verify(repository).save(savedUser.capture())
            // Document the bug: password IS null when saving cached user
            assertNull(savedUser.firstValue.password,
                "BUG-1: Cached user has null password — will cause DataIntegrityViolationException in production")
        }

        @Test
        fun `should not save when nothing changed`() {
            val user = buildUser(displayName = "Same Name")
            setSecurityContext(user)

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn "Same Name" // same as current
                on { avatar() } doReturn null
            }

            userService.updateCurrentProfile(dto)

            verify(repository, never()).save(any())
        }

        @Test
        fun `should not save when displayName is null and no avatar`() {
            val user = buildUser()
            setSecurityContext(user)

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn null
                on { avatar() } doReturn null
            }

            userService.updateCurrentProfile(dto)

            verify(repository, never()).save(any())
        }

        @Test
        fun `should throw when displayName is blank (only spaces)`() {
            val user = buildUser()
            setSecurityContext(user)

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn "   "
            }

            val ex = org.junit.jupiter.api.assertThrows<ApiException> {
                userService.updateCurrentProfile(dto)
            }

            assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        }

        @Test
        fun `should throw when displayName exceeds 60 characters`() {
            val user = buildUser()
            setSecurityContext(user)

            val longName = "A".repeat(61)
            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn longName
            }

            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.updateCurrentProfile(dto)
            }
        }

        @Test
        fun `should accept displayName at exactly 60 characters`() {
            val user = buildUser()
            setSecurityContext(user)

            val exactName = "A".repeat(60)
            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn exactName
                on { avatar() } doReturn null
            }

            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.updateCurrentProfile(dto)

            verify(repository).save(any<User>())
        }

        @Test
        fun `should trim displayName whitespace`() {
            val user = buildUser(displayName = "Old")
            setSecurityContext(user)

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn "  New Name  "
                on { avatar() } doReturn null
            }

            val savedUser = argumentCaptor<User>()
            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.updateCurrentProfile(dto)

            verify(repository).save(savedUser.capture())
            assertEquals("New Name", savedUser.firstValue.displayName)
        }

        @Test
        fun `should skip empty avatar file`() {
            val user = buildUser(displayName = "Old")
            setSecurityContext(user)

            val emptyAvatar = mock<MultipartFile> {
                on { isEmpty } doReturn true
            }

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn "New Name"
                on { avatar() } doReturn emptyAvatar
            }

            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.updateCurrentProfile(dto)

            verify(s3Service, never()).uploadAvatar(any())
        }

        @Test
        fun `should throw when avatar is not an image type`() {
            val user = buildUser()
            setSecurityContext(user)

            val videoFile = mock<MultipartFile> {
                on { isEmpty } doReturn false
                on { contentType } doReturn "application/pdf"
                on { originalFilename } doReturn "doc.pdf"
            }

            val dto = mock<UserProfileUpdateDto> {
                on { displayName() } doReturn null
                on { avatar() } doReturn videoFile
            }

            whenever(fileTypeService.getMimeType("application/pdf")).thenReturn("application")
            whenever(fileTypeService.getFileExtension("doc.pdf")).thenReturn("pdf")
            whenever(fileTypeService.checkTypeInFileType("application", "pdf")).thenReturn(Attachment.FileType.DOCUMENT)

            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.updateCurrentProfile(dto)
            }

            verify(s3Service, never()).uploadAvatar(any())
        }
    }

    // =========================================================================
    // createUser — edge cases
    // =========================================================================

    @Nested
    @DisplayName("createUser — edge cases")
    inner class CreateUserEdgeCases {

        @Test
        fun `should encode password before saving`() {
            val dto = UserDto("newuser", "plaintext123")

            whenever(repository.existsByUsername("newuser")).thenReturn(false)
            whenever(passwordEncoder.encode("plaintext123")).thenReturn("hashed_value")
            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.createUser(dto)

            val saved = argumentCaptor<User>()
            verify(repository).save(saved.capture())
            assertEquals("hashed_value", saved.firstValue.password)
        }

        @Test
        fun `should set displayName to username when creating`() {
            val dto = UserDto("myuser", "password123")

            whenever(repository.existsByUsername("myuser")).thenReturn(false)
            whenever(passwordEncoder.encode(any())).thenReturn("encoded")
            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.createUser(dto)

            val saved = argumentCaptor<User>()
            verify(repository).save(saved.capture())
            assertEquals("myuser", saved.firstValue.displayName)
        }
    }

    // =========================================================================
    // changePassword — edge cases
    // =========================================================================

    @Nested
    @DisplayName("changePassword — edge cases")
    inner class ChangePasswordEdgeCases {

        @Test
        fun `should throw when new password is exactly 7 characters (boundary)`() {
            // validateNewPassword runs BEFORE getUserOrThrows, no SecurityContext needed
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.changePassword("oldPassword", "1234567")
            }
        }

        @Test
        fun `should accept new password at exactly 8 characters (boundary)`() {
            val user = buildUser()
            setSecurityContext(user)

            whenever(passwordEncoder.matches("oldPassword", "encoded_password")).thenReturn(true)
            whenever(passwordEncoder.matches("12345678", "encoded_password")).thenReturn(false)
            whenever(passwordEncoder.encode("12345678")).thenReturn("new_encoded")
            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.changePassword("oldPassword", "12345678")

            verify(repository).save(any<User>())
        }

        @Test
        fun `should throw when new password is whitespace-only (trims to less than 8)`() {
            // validateNewPassword runs first → throws before getUserOrThrows
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.changePassword("oldPassword", "        ") // 8 spaces → trim → empty
            }
        }

        @Test
        fun `should throw when new password is null-like empty`() {
            // validateNewPassword runs first → throws before getUserOrThrows
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.changePassword("oldPassword", "")
            }
        }

        @Test
        fun `should invalidate cache after password change`() {
            val user = buildUser()
            setSecurityContext(user)

            whenever(passwordEncoder.matches("oldPass", "encoded_password")).thenReturn(true)
            whenever(passwordEncoder.matches("newPassword1", "encoded_password")).thenReturn(false)
            whenever(passwordEncoder.encode("newPassword1")).thenReturn("new_enc")
            whenever(repository.save(any<User>())).thenAnswer { it.arguments[0] as User }

            userService.changePassword("oldPass", "newPassword1")

            verify(userCacheService).invalidateUserCache("testuser")
            verify(passwordResetTokenService).revokeByUsername("testuser")
        }
    }

    // =========================================================================
    // resetPassword — edge cases
    // =========================================================================

    @Nested
    @DisplayName("resetPassword — edge cases")
    inner class ResetPasswordEdgeCases {

        @Test
        fun `should throw when new password same as current`() {
            val user = buildUser()
            whenever(passwordResetTokenService.validateToken("token")).thenReturn("testuser")
            whenever(repository.findByUsername("testuser")).thenReturn(Optional.of(user))
            whenever(passwordEncoder.matches("samePassword", "encoded_password")).thenReturn(true)

            val ex = org.junit.jupiter.api.assertThrows<ApiException> {
                userService.resetPassword("token", "samePassword")
            }

            assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        }

        @Test
        fun `should throw when user not found for valid token`() {
            whenever(passwordResetTokenService.validateToken("orphan-token")).thenReturn("deleteduser")
            whenever(repository.findByUsername("deleteduser")).thenReturn(Optional.empty())

            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.resetPassword("orphan-token", "newPassword1")
            }
        }

        @Test
        fun `should validate password length before token validation`() {
            // validateNewPassword is called first → should throw before even checking token
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.resetPassword("any-token", "short")
            }

            // Token validation should never be called
            verify(passwordResetTokenService, never()).validateToken(any())
        }
    }

    // =========================================================================
    // searchUser — edge cases
    // =========================================================================

    @Nested
    @DisplayName("searchUser — edge cases")
    inner class SearchUserEdgeCases {

        @Test
        fun `should throw when limit is 0`() {
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.searchUser("test", 0)
            }
        }

        @Test
        fun `should throw when limit is negative`() {
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.searchUser("test", -1)
            }
        }

        @Test
        fun `should throw when limit is 21 (above max)`() {
            org.junit.jupiter.api.assertThrows<ApiException> {
                userService.searchUser("test", 21)
            }
        }

        @Test
        fun `should accept limit at exactly 20 (boundary max)`() {
            val user = buildUser()
            setSecurityContext(user)

            whenever(repository.searchByKeyword(eq("test"), eq(1L), any<PageRequest>()))
                .thenReturn(emptyList())

            val results = userService.searchUser("test", 20)

            assertTrue(results.isEmpty())
        }

        @Test
        fun `should accept limit at exactly 1 (boundary min)`() {
            val user = buildUser()
            setSecurityContext(user)

            whenever(repository.searchByKeyword(eq("test"), eq(1L), any<PageRequest>()))
                .thenReturn(emptyList())

            val results = userService.searchUser("test", 1)

            assertTrue(results.isEmpty())
        }

        @Test
        fun `should return empty for whitespace-only keyword`() {
            val results = userService.searchUser("   \t  ", 10)

            assertTrue(results.isEmpty())
            verify(repository, never()).searchByKeyword(any(), any(), any<PageRequest>())
        }

        @Test
        fun `should trim keyword before searching`() {
            val user = buildUser()
            setSecurityContext(user)

            whenever(repository.searchByKeyword(eq("hello"), eq(1L), any<PageRequest>()))
                .thenReturn(emptyList())

            userService.searchUser("  hello  ", 10)

            verify(repository).searchByKeyword(eq("hello"), eq(1L), any<PageRequest>())
        }
    }

    // =========================================================================
    // getUserByAuthentication — edge cases
    // =========================================================================

    @Nested
    @DisplayName("getUserByAuthentication — edge cases")
    inner class GetUserByAuthEdgeCases {

        @Test
        fun `should return empty when principal is unknown type`() {
            val authentication = mock<Authentication> {
                on { principal } doReturn "string_principal" // unexpected type
            }

            val result = userService.getUserByAuthentication(authentication)

            assertTrue(result.isEmpty)
        }

        @Test
        fun `should use cache when principal is JWT`() {
            val jwt = mock<Jwt> {
                on { subject } doReturn "cached_user"
            }
            val authentication = mock<Authentication> {
                on { principal } doReturn jwt
            }

            val user = buildUser(username = "cached_user")
            whenever(userCacheService.getCachedUser("cached_user")).thenReturn(Optional.of(user))

            val result = userService.getUserByAuthentication(authentication)

            assertTrue(result.isPresent)
            verify(userCacheService).getCachedUser("cached_user")
        }

        @Test
        fun `should return empty when JWT user not found in cache or DB`() {
            val jwt = mock<Jwt> {
                on { subject } doReturn "deleted_user"
            }
            val authentication = mock<Authentication> {
                on { principal } doReturn jwt
            }

            whenever(userCacheService.getCachedUser("deleted_user")).thenReturn(Optional.empty())

            val result = userService.getUserByAuthentication(authentication)

            assertTrue(result.isEmpty)
        }
    }

    // =========================================================================
    // requestPasswordReset — edge cases
    // =========================================================================

    @Nested
    @DisplayName("requestPasswordReset — edge cases")
    inner class RequestPasswordResetEdgeCases {

        @Test
        fun `should do nothing for empty string`() {
            userService.requestPasswordReset("")

            verify(repository, never()).findByUsername(any())
        }

        @Test
        fun `should do nothing for whitespace-only string`() {
            userService.requestPasswordReset("   \t  ")

            // trim() → empty → return early
            verify(repository, never()).findByUsername(any())
        }

        @Test
        fun `should trim username before lookup`() {
            whenever(repository.findByUsername("alice")).thenReturn(Optional.empty())

            userService.requestPasswordReset("  alice  ")

            verify(repository).findByUsername("alice")
        }

        @Test
        fun `should not leak info when user does not exist`() {
            whenever(repository.findByUsername("ghost")).thenReturn(Optional.empty())

            // Should NOT throw — silently returns
            userService.requestPasswordReset("ghost")

            verify(emailService, never()).sendPasswordResetEmail(any(), any(), any())
        }
    }
}
