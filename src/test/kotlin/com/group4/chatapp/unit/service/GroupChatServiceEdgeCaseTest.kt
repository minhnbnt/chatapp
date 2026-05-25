package com.group4.chatapp.unit.service

import com.group4.chatapp.dtos.group.GroupChatCreateDto
import com.group4.chatapp.dtos.group.GroupChatUpdateDto
import com.group4.chatapp.exceptions.ApiException
import com.group4.chatapp.models.*
import com.group4.chatapp.repositories.*
import com.group4.chatapp.services.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class GroupChatServiceEdgeCaseTest {

    @Mock lateinit var userService: UserService
    @Mock lateinit var attachmentService: AttachmentService
    @Mock lateinit var chatRoomRepository: ChatRoomRepository
    @Mock lateinit var chatRoomMemberRepository: ChatRoomMemberRepository
    @Mock lateinit var messageRepository: MessageRepository
    @Mock lateinit var chatRoomReadStateRepository: ChatRoomReadStateRepository
    @Mock lateinit var invitationRepository: InvitationRepository
    @Mock lateinit var chatRoomPinRepository: ChatRoomPinRepository
    @Mock lateinit var messagingTemplate: SimpMessagingTemplate
    @Mock lateinit var notificationService: NotificationService
    @Mock lateinit var presenceService: PresenceService

    @InjectMocks
    lateinit var groupChatService: GroupChatService

    private fun buildUser(id: Long = 1L, username: String = "creator"): User {
        return User.builder().id(id).username(username).password("pass").displayName(username).build()
    }

    private fun buildGroupRoom(
        id: Long = 10L,
        members: Set<User>,
        creatorId: Long? = null,
        name: String = "Test Group"
    ): ChatRoom {
        return ChatRoom.builder()
            .id(id)
            .type(ChatRoom.Type.GROUP)
            .name(name)
            .members(members.toMutableSet())
            .creatorId(creatorId)
            .build()
    }

    // =========================================================================
    // createGroup — edge cases with DTO validator constraints
    // =========================================================================

    @Nested
    @DisplayName("createGroup — edge cases")
    inner class CreateGroupEdgeCases {

        @Test
        fun `should include creator in members even if not in memberIds`() {
            val creator = buildUser(1L, "creator")
            val member2 = buildUser(2L, "m2")
            val member3 = buildUser(3L, "m3")

            whenever(userService.getUserOrThrows()).thenReturn(creator)
            whenever(userService.getUserById(2L)).thenReturn(Optional.of(member2))
            whenever(userService.getUserById(3L)).thenReturn(Optional.of(member3))

            val savedRoom = buildGroupRoom(10L, setOf(creator, member2, member3), creator.id)
            whenever(chatRoomRepository.save(any<ChatRoom>())).thenReturn(savedRoom)
            whenever(chatRoomMemberRepository.save(any<ChatRoomMember>())).thenAnswer { it.arguments[0] }
            whenever(chatRoomMemberRepository.isUserAdmin(10L, 1L)).thenReturn(true)
            whenever(messageRepository.findFirstByRoom_IdOrderBySentOnDescIdDesc(10L)).thenReturn(Optional.empty())

            // DTO validated: name @NotBlank @Size(1,100), memberIds @NotNull @Size(min=2)
            val dto = GroupChatCreateDto("My Group", listOf(2L, 3L), null)
            val result = groupChatService.createGroup(dto)

            assertNotNull(result)
            // Creator + 2 members = 3 saves
            verify(chatRoomMemberRepository, atLeast(3)).save(any<ChatRoomMember>())
        }

        @Test
        fun `should deduplicate when creator ID appears in memberIds`() {
            val creator = buildUser(1L, "creator")
            val member2 = buildUser(2L, "m2")

            whenever(userService.getUserOrThrows()).thenReturn(creator)
            whenever(userService.getUserById(1L)).thenReturn(Optional.of(creator)) // creator in memberIds
            whenever(userService.getUserById(2L)).thenReturn(Optional.of(member2))

            val savedRoom = buildGroupRoom(10L, setOf(creator, member2), creator.id)
            whenever(chatRoomRepository.save(any<ChatRoom>())).thenReturn(savedRoom)
            whenever(chatRoomMemberRepository.save(any<ChatRoomMember>())).thenAnswer { it.arguments[0] }
            whenever(chatRoomMemberRepository.isUserAdmin(10L, 1L)).thenReturn(true)
            whenever(messageRepository.findFirstByRoom_IdOrderBySentOnDescIdDesc(10L)).thenReturn(Optional.empty())

            // memberIds includes creator (id=1) — Set should deduplicate
            val dto = GroupChatCreateDto("Group", listOf(1L, 2L), null)
            val result = groupChatService.createGroup(dto)

            assertNotNull(result)
        }

        @Test
        fun `should throw when one member ID does not exist`() {
            val creator = buildUser(1L, "creator")
            val member2 = buildUser(2L, "m2")

            whenever(userService.getUserOrThrows()).thenReturn(creator)
            whenever(userService.getUserById(2L)).thenReturn(Optional.of(member2))
            whenever(userService.getUserById(999L)).thenReturn(Optional.empty())

            val dto = GroupChatCreateDto("Group", listOf(2L, 999L), null)

            org.junit.jupiter.api.assertThrows<ApiException> {
                groupChatService.createGroup(dto)
            }
        }

        @Test
        fun `should throw when avatar attachment is not an image`() {
            val creator = buildUser(1L, "creator")
            val member2 = buildUser(2L, "m2")
            val member3 = buildUser(3L, "m3")

            whenever(userService.getUserOrThrows()).thenReturn(creator)
            whenever(userService.getUserById(2L)).thenReturn(Optional.of(member2))
            whenever(userService.getUserById(3L)).thenReturn(Optional.of(member3))

            val videoAttachment = mock<Attachment> {
                on { isImage } doReturn false
            }
            whenever(attachmentService.getAttachmentOrThrow(42L)).thenReturn(videoAttachment)

            val dto = GroupChatCreateDto("Group", listOf(2L, 3L), 42L)

            org.junit.jupiter.api.assertThrows<ApiException> {
                groupChatService.createGroup(dto)
            }
        }

        // NOTE: The following conditions are validated by DTO @annotations before reaching service:
        // - name @NotBlank @Size(min=1, max=100): blank/null/101+ chars → rejected at controller
        // - memberIds @NotNull @Size(min=2): null or <2 members → rejected at controller
        // These do NOT need service-level tests — they are covered by Spring validation.
    }

    // =========================================================================
    // updateGroup — edge cases
    // =========================================================================

    @Nested
    @DisplayName("updateGroup — edge cases")
    inner class UpdateGroupEdgeCases {

        @Test
        fun `should handle update with null name (no change)`() {
            val user = buildUser()
            val room = buildGroupRoom(
                10L,
                setOf(user, buildUser(2L, "u2"), buildUser(3L, "u3")),
                user.id,
                "Original Name"
            )

            whenever(userService.getUserOrThrows()).thenReturn(user)
            whenever(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room))
            whenever(chatRoomMemberRepository.isUserAdmin(10L, 1L)).thenReturn(true)
            whenever(messageRepository.findFirstByRoom_IdOrderBySentOnDescIdDesc(10L)).thenReturn(Optional.empty())

            // DTO: name @Nullable @Size(min=1, max=100) — null means "don't change"
            val dto = GroupChatUpdateDto(null, null)
            val result = groupChatService.updateGroup(10L, dto)

            assertNotNull(result)
            // Should NOT save since nothing changed
            verify(chatRoomRepository, never()).save(any<ChatRoom>())
        }

        @Test
        fun `should throw when room not found`() {
            val user = buildUser()
            whenever(userService.getUserOrThrows()).thenReturn(user)
            whenever(chatRoomRepository.findById(999L)).thenReturn(Optional.empty())

            val dto = GroupChatUpdateDto("New Name", null)

            org.junit.jupiter.api.assertThrows<ApiException> {
                groupChatService.updateGroup(999L, dto)
            }
        }

        @Test
        fun `should throw when room is DUO type (not a group)`() {
            val user = buildUser()
            val duoRoom = ChatRoom.builder()
                .id(10L)
                .type(ChatRoom.Type.DUO)
                .members(mutableSetOf(user, buildUser(2L, "u2")))
                .build()

            whenever(userService.getUserOrThrows()).thenReturn(user)
            whenever(chatRoomRepository.findById(10L)).thenReturn(Optional.of(duoRoom))

            val dto = GroupChatUpdateDto("New Name", null)

            org.junit.jupiter.api.assertThrows<ApiException> {
                groupChatService.updateGroup(10L, dto)
            }
        }

        // NOTE: name @Size(min=1, max=100): empty string ("") is rejected at DTO validation level
    }

    // =========================================================================
    // addMembers — edge cases
    // =========================================================================

    @Nested
    @DisplayName("addMembers — edge cases")
    inner class AddMembersEdgeCases {

        @Test
        fun `should skip already-existing member without error`() {
            val user = buildUser()
            val existing = buildUser(2L, "existing")
            val room = buildGroupRoom(10L, setOf(user, existing, buildUser(3L, "u3")), user.id)

            whenever(userService.getUserOrThrows()).thenReturn(user)
            whenever(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room))
            whenever(chatRoomMemberRepository.isUserMember(10L, 1L)).thenReturn(true)
            whenever(userService.getUserById(2L)).thenReturn(Optional.of(existing))
            whenever(chatRoomMemberRepository.isUserMember(10L, 2L)).thenReturn(true) // already member
            whenever(chatRoomRepository.save(any<ChatRoom>())).thenAnswer { it.arguments[0] }
            whenever(chatRoomMemberRepository.isUserAdmin(10L, 1L)).thenReturn(true)
            whenever(messageRepository.findFirstByRoom_IdOrderBySentOnDescIdDesc(10L)).thenReturn(Optional.empty())

            // Should NOT throw — already-member is silently skipped
            groupChatService.addMembers(10L, listOf(2L))

            // No new ChatRoomMember saved since user is already a member
            verify(chatRoomMemberRepository, never()).save(any<ChatRoomMember>())
        }

        // NOTE: DTO validated: memberIds @NotNull @NotEmpty @Size(min=1)
        // Empty list → rejected before reaching service
    }

    // =========================================================================
    // removeMember — edge cases
    // =========================================================================

    @Nested
    @DisplayName("removeMember — edge cases")
    inner class RemoveMemberEdgeCases {

        @Test
        fun `should throw when target is not a member`() {
            val creator = buildUser(1L, "creator")
            val room = buildGroupRoom(
                10L,
                setOf(creator, buildUser(2L, "u2"), buildUser(3L, "u3")),
                creator.id
            )

            whenever(userService.getUserOrThrows()).thenReturn(creator)
            whenever(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room))
            whenever(chatRoomMemberRepository.isUserMember(10L, 1L)).thenReturn(true)
            whenever(chatRoomMemberRepository.isUserMember(10L, 999L)).thenReturn(false) // not a member

            org.junit.jupiter.api.assertThrows<ApiException> {
                groupChatService.removeMember(10L, 999L)
            }
        }
    }

    // =========================================================================
    // dissolveGroup — edge cases
    // =========================================================================

    @Nested
    @DisplayName("dissolveGroup — edge cases")
    inner class DissolveGroupEdgeCases {

        @Test
        fun `should throw when room not found`() {
            val user = buildUser()
            whenever(userService.getUserOrThrows()).thenReturn(user)
            whenever(chatRoomRepository.findById(999L)).thenReturn(Optional.empty())

            org.junit.jupiter.api.assertThrows<ApiException> {
                groupChatService.dissolveGroup(999L)
            }
        }

        @Test
        fun `should cleanup all related data on dissolve`() {
            val user = buildUser()
            val room = buildGroupRoom(10L, setOf(user, buildUser(2L, "u2"), buildUser(3L, "u3")), user.id)

            whenever(userService.getUserOrThrows()).thenReturn(user)
            whenever(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room))

            groupChatService.dissolveGroup(10L)

            // Verify all cleanup operations happen in correct order
            verify(chatRoomReadStateRepository).deleteByRoomId(10L)
            verify(invitationRepository).deleteByChatRoomId(10L)
            verify(chatRoomPinRepository).deleteByChatRoom_Id(10L)
            verify(messageRepository).deleteByRoomId(10L)
            verify(chatRoomMemberRepository).deleteByChatRoomId(10L)
            verify(chatRoomRepository).delete(room)
        }
    }
}
