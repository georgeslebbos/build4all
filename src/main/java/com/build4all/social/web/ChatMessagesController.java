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
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @PreAuthorize("hasRole('USER')")
    public ChatMessageDto sendMessageWithImage(@PathVariable Long receiverId,
                                               @RequestParam(required = false) String message,
                                               @RequestPart(required = false) MultipartFile image,
                                               @RequestParam Long adminId,
                                               @RequestParam Long projectId,
                                               Principal principal) {
        Users sender = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        Users receiver = usersService.getUserById(receiverId, adminId, projectId);

        if (sender == null || receiver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender or receiver not found.");
        }

        String imageUrl = (image != null && !image.isEmpty()) ? chatService.uploadImage(image) : null;

        ChatMessages chat = chatService.sendMessageWithImage(sender, receiver, message, imageUrl);
        return new ChatMessageDto(chat, sender.getId());
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping("/conversation/{userId}")
    @PreAuthorize("hasRole('USER')")
    public List<ChatMessageDto> getConversation(@PathVariable Long userId,
                                                @RequestParam Long adminId,
                                                @RequestParam Long projectId,
                                                Principal principal) {
        Users currentUser = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        Users otherUser   = usersService.getUserById(userId, adminId, projectId);

        if (currentUser == null || otherUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        chatService.markMessagesAsRead(currentUser, otherUser);

        List<ChatMessages> messages = chatService.getConversation(currentUser, otherUser);
        return messages.stream()
                .map(msg -> new ChatMessageDto(msg, currentUser.getId()))
                .collect(Collectors.toList());
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public List<ChatMessageDto> getMyMessages(@RequestParam Long adminId,
                                              @RequestParam Long projectId,
                                              Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        List<ChatMessages> messages = chatService.getMessagesByUser(user);
        return messages.stream()
                .map(msg -> new ChatMessageDto(msg, user.getId()))
                .collect(Collectors.toList());
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping("/count/my")
    @PreAuthorize("hasRole('USER')")
    public Long countMyMessages(@RequestParam Long adminId,
                                @RequestParam Long projectId,
                                Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        return chatService.countAllMessagesForUser(user);
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping("/count/by-contact")
    @PreAuthorize("hasRole('USER')")
    public List<ContactMessageCountDto> countMessagesByContact(@RequestParam Long adminId,
                                                               @RequestParam Long projectId,
                                                               Principal principal) {
        Users currentUser = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        List<Object[]> counts = chatService.countMessagesGroupedByContact(currentUser);
        return counts.stream()
                .map(obj -> new ContactMessageCountDto((Long) obj[0], (Long) obj[1]))
                .collect(Collectors.toList());
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping("/unread/by-contact")
    @PreAuthorize("hasRole('USER')")
    public List<ContactMessageCountDto> countUnreadByContact(@RequestParam Long adminId,
                                                             @RequestParam Long projectId,
                                                             Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        List<Object[]> unreadCounts = chatService.countUnreadMessagesGroupedByContact(user.getId());
        return unreadCounts.stream()
                .map(obj -> new ContactMessageCountDto((Long) obj[0], (Long) obj[1]))
                .collect(Collectors.toList());
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public void deleteMessage(@PathVariable Long messageId,
                              @RequestParam Long adminId,
                              @RequestParam Long projectId,
                              Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        boolean deleted = chatService.deleteMessageByIdAndUser(messageId, user);

        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this message");
        }
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
        @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @PatchMapping("/{messageId}/read")
    @PreAuthorize("hasRole('USER')")
    public void markMessageAsRead(@PathVariable Long messageId,
                                  @RequestParam Long adminId,
                                  @RequestParam Long projectId,
                                  Principal principal) {
        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        chatService.markSingleMessageAsRead(user, messageId);
    }
}
