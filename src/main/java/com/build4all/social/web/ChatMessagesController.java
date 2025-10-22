package com.build4all.social.web;

import com.build4all.social.dto.ChatMessageDto;
import com.build4all.social.dto.ContactMessageCountDto;
import com.build4all.social.domain.ChatMessages;
import com.build4all.user.domain.Users;
import com.build4all.social.repository.FriendshipRepository;
import com.build4all.social.service.ChatMessagesService;
import com.build4all.user.service.UserService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class ChatMessagesController {

    private final ChatMessagesService chatService;
    private final UserService usersService;
    private final FriendshipRepository friendshipRepo;

    public ChatMessagesController(ChatMessagesService chatService, UserService usersService, FriendshipRepository friendshipRepo) {
        this.chatService = chatService;
        this.usersService = usersService;
        this.friendshipRepo = friendshipRepo;
    }

    @PostMapping(value = "/send/{receiverId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400"),
        @ApiResponse(responseCode = "401"),
        @ApiResponse(responseCode = "403"),
        @ApiResponse(responseCode = "404"),
        @ApiResponse(responseCode = "500")
    })
    @PreAuthorize("hasRole('USER')")
    public ChatMessageDto sendMessageWithImage(@PathVariable Long receiverId,
                                               @RequestParam(required = false) String message,
                                               @RequestPart(required = false) MultipartFile image,
                                               @RequestParam Long ownerProjectLinkId,
                                               Principal principal) {
        Users sender   = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        Users receiver = usersService.getUserById(receiverId, ownerProjectLinkId);

        if (sender == null || receiver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender or receiver not found.");
        }

        String imageUrl = (image != null && !image.isEmpty()) ? chatService.uploadImage(image) : null;

        ChatMessages chat = chatService.sendMessageWithImage(sender, receiver, message, imageUrl);
        return new ChatMessageDto(chat, sender.getId());
    }

    @GetMapping("/conversation/{userId}")
    @PreAuthorize("hasRole('USER')")
    public List<ChatMessageDto> getConversation(@PathVariable Long userId,
                                                @RequestParam Long ownerProjectLinkId,
                                                Principal principal) {
        Users currentUser = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        Users otherUser   = usersService.getUserById(userId, ownerProjectLinkId);

        if (currentUser == null || otherUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        chatService.markMessagesAsRead(currentUser, otherUser);

        List<ChatMessages> messages = chatService.getConversation(currentUser, otherUser);
        return messages.stream()
                .map(msg -> new ChatMessageDto(msg, currentUser.getId()))
                .collect(Collectors.toList());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public List<ChatMessageDto> getMyMessages(@RequestParam Long ownerProjectLinkId,
                                              Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        List<ChatMessages> messages = chatService.getMessagesByUser(user);
        return messages.stream()
                .map(msg -> new ChatMessageDto(msg, user.getId()))
                .collect(Collectors.toList());
    }

    @GetMapping("/count/my")
    @PreAuthorize("hasRole('USER')")
    public Long countMyMessages(@RequestParam Long ownerProjectLinkId,
                                Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        return chatService.countAllMessagesForUser(user);
    }

    @GetMapping("/count/by-contact")
    @PreAuthorize("hasRole('USER')")
    public List<ContactMessageCountDto> countMessagesByContact(@RequestParam Long ownerProjectLinkId,
                                                               Principal principal) {
        Users currentUser = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        List<Object[]> counts = chatService.countMessagesGroupedByContact(currentUser);
        return counts.stream()
                .map(obj -> new ContactMessageCountDto((Long) obj[0], (Long) obj[1]))
                .collect(Collectors.toList());
    }

    @GetMapping("/unread/by-contact")
    @PreAuthorize("hasRole('USER')")
    public List<ContactMessageCountDto> countUnreadByContact(@RequestParam Long ownerProjectLinkId,
                                                             Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        List<Object[]> unreadCounts = chatService.countUnreadMessagesGroupedByContact(user.getId());
        return unreadCounts.stream()
                .map(obj -> new ContactMessageCountDto((Long) obj[0], (Long) obj[1]))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public void deleteMessage(@PathVariable Long messageId,
                              @RequestParam Long ownerProjectLinkId,
                              Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        boolean deleted = chatService.deleteMessageByIdAndUser(messageId, user);

        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this message");
        }
    }

    @PatchMapping("/{messageId}/read")
    @PreAuthorize("hasRole('USER')")
    public void markMessageAsRead(@PathVariable Long messageId,
                                  @RequestParam Long ownerProjectLinkId,
                                  Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        chatService.markSingleMessageAsRead(user, messageId);
    }
}
