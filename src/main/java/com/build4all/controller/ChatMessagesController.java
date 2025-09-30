package com.build4all.controller;

import com.build4all.dto.ChatMessageDto;

import com.build4all.dto.ContactMessageCountDto;
import com.build4all.entities.ChatMessages;
import com.build4all.entities.Users;
import com.build4all.repositories.FriendshipRepository;
import com.build4all.services.ChatMessagesService;
import com.build4all.services.UserService;

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
    @PreAuthorize("hasRole('USER')")  // <----- Add this annotation to require user role token
    public ChatMessageDto sendMessageWithImage(@PathVariable Long receiverId,
                                               @RequestParam(required = false) String message,
                                               @RequestPart(required = false) MultipartFile image,
                                               Principal principal) {
        // Your original method body untouched
        Users sender = usersService.getUserByEmaill(principal.getName());
        Users receiver = usersService.getUserById(receiverId);

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
    	@PreAuthorize("hasRole('USER')")  // <-- This enforces the user token
    	public List<ChatMessageDto> getConversation(@PathVariable Long userId, Principal principal) {
    	    Users currentUser = usersService.getUserByEmaill(principal.getName());
    	    Users otherUser = usersService.getUserById(userId);

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
    	@PreAuthorize("hasRole('USER')")  // <--- Add user token enforcement here
    	public List<ChatMessageDto> getMyMessages(Principal principal) {
    	    Users user = usersService.getUserByEmaill(principal.getName());
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
    	@PreAuthorize("hasRole('USER')")  // <-- Add user token check here
    	public Long countMyMessages(Principal principal) {
    	    Users user = usersService.getUserByEmaill(principal.getName());
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
    	@PreAuthorize("hasRole('USER')")  // Add user token check here
    	public List<ContactMessageCountDto> countMessagesByContact(Principal principal) {
    	    Users currentUser = usersService.getUserByEmaill(principal.getName());
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
    	public List<ContactMessageCountDto> countUnreadByContact(Principal principal) {
    	    Users user = usersService.getUserByEmaill(principal.getName());
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
    	public void deleteMessage(@PathVariable Long messageId, Principal principal) {
    	    Users user = usersService.getUserByEmaill(principal.getName());
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
    	public void markMessageAsRead(@PathVariable Long messageId, Principal principal) {
    	    Users user = usersService.getUserByEmaill(principal.getName());
    	    chatService.markSingleMessageAsRead(user, messageId);
    	}

}
