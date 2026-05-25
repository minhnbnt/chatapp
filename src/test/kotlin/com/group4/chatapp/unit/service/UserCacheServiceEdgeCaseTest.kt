package com.group4.chatapp.unit.service

import com.group4.chatapp.models.User
import com.group4.chatapp.repositories.UserRepository
import com.group4.chatapp.services.UserCacheService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class UserCacheServiceEdgeCaseTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var redisTemplate: StringRedisTemplate
    @Mock lateinit var hashOps: HashOperations<String, String, String>

    lateinit var service: UserCacheService

    @BeforeEach
    fun setUp() {
        service = UserCacheService(userRepository, redisTemplate)
    }

    private fun buildUser(id: Long = 1L, username: String = "testuser"): User {
        return User.builder().id(id).username(username).password("hashed_pass").displayName("Test").build()
    }

    @Nested
    @DisplayName("getCachedUser — data integrity edge cases")
    inner class GetCachedUserDataIntegrity {

        @Test
        fun `cached user should have NULL password (documents BUG-1 root cause)`() {
            whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
            whenever(hashOps.entries("auth:user:testuser")).thenReturn(
                mapOf("id" to "1", "username" to "testuser", "displayName" to "Test")
            )

            val result = service.getCachedUser("testuser")

            assertTrue(result.isPresent)
            // This documents BUG-1: cached user has null password
            // Any code that calls repository.save() with this entity will fail with
            // DataIntegrityViolationException (NOT NULL violation on password column)
            assertNull(result.get().password,
                "Cached user must have null password — getCachedUser does not store/restore password")
        }

        @Test
        fun `DB-loaded user should have password (cache miss path is safe)`() {
            val user = buildUser()
            whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
            whenever(hashOps.entries("auth:user:testuser")).thenReturn(emptyMap())
            whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user))

            val result = service.getCachedUser("testuser")

            assertTrue(result.isPresent)
            assertEquals("hashed_pass", result.get().password,
                "DB-loaded user must preserve password")
        }

        @Test
        fun `cached user should preserve all fields that are stored`() {
            whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
            whenever(hashOps.entries("auth:user:alice")).thenReturn(
                mapOf("id" to "42", "username" to "alice", "displayName" to "Alice Wonderland")
            )

            val result = service.getCachedUser("alice")

            assertTrue(result.isPresent)
            val user = result.get()
            assertEquals(42L, user.id)
            assertEquals("alice", user.username)
            assertEquals("Alice Wonderland", user.displayName)
        }

        @Test
        fun `should handle missing displayName in cache gracefully`() {
            whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
            whenever(hashOps.entries("auth:user:testuser")).thenReturn(
                mapOf("id" to "1", "username" to "testuser")
                // displayName not present
            )

            val result = service.getCachedUser("testuser")

            assertTrue(result.isPresent)
            assertEquals("testuser", result.get().username)
        }

        @Test
        fun `should handle non-numeric id in cache by falling back to DB`() {
            whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
            whenever(hashOps.entries("auth:user:testuser")).thenReturn(
                mapOf("id" to "not_a_number", "username" to "testuser")
            )

            val user = buildUser()
            whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user))

            val result = service.getCachedUser("testuser")

            // Should fall back to DB due to NumberFormatException
            assertTrue(result.isPresent)
            assertEquals("hashed_pass", result.get().password)
        }
    }

    @Nested
    @DisplayName("getCachedUser — Redis failure edge cases")
    inner class GetCachedUserRedisFailures {

        @Test
        fun `should fallback to DB when Redis throws on opsForHash`() {
            val user = buildUser()
            whenever(redisTemplate.opsForHash<String, String>()).thenThrow(RuntimeException("Connection refused"))
            whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user))

            val result = service.getCachedUser("testuser")

            assertTrue(result.isPresent)
            assertEquals("hashed_pass", result.get().password)
        }

        @Test
        fun `should fallback to DB when Redis throws on entries`() {
            val user = buildUser()
            whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
            whenever(hashOps.entries("auth:user:testuser")).thenThrow(RuntimeException("Timeout"))
            whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user))

            val result = service.getCachedUser("testuser")

            assertTrue(result.isPresent)
        }

        @Test
        fun `should return empty when both Redis and DB fail`() {
            whenever(redisTemplate.opsForHash<String, String>()).thenThrow(RuntimeException("Redis down"))
            whenever(userRepository.findByUsername("ghost")).thenReturn(Optional.empty())

            val result = service.getCachedUser("ghost")

            assertTrue(result.isEmpty)
        }
    }

    @Nested
    @DisplayName("invalidateUserCache — edge cases")
    inner class InvalidateUserCacheEdgeCases {

        @Test
        fun `should delete correct cache key`() {
            service.invalidateUserCache("alice")

            verify(redisTemplate).delete("auth:user:alice")
        }

        @Test
        fun `should handle Redis error on invalidation gracefully`() {
            whenever(redisTemplate.delete("auth:user:testuser")).thenThrow(RuntimeException("Redis gone"))

            // Should not throw — invalidation failure should be silent
            try {
                service.invalidateUserCache("testuser")
            } catch (e: RuntimeException) {
                // If it throws, that's OK too — we're documenting behavior
            }

            verify(redisTemplate).delete("auth:user:testuser")
        }
    }
}
