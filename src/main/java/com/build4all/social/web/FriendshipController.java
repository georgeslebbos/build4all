package com.build4all.social.web;

import com.build4all.social.domain.Friendship;
import com.build4all.user.domain.Users;
import com.build4all.social.service.FriendshipService;
import com.build4all.user.service.UserService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final UserService userService;

    public FriendshipController(FriendshipService friendshipService, UserService userService) {
        this.friendshipService = friendshipService;
        this.userService = userService;
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
    @PostMapping("/add/{friendId}")
    public ResponseEntity<?> sendFriendRequest(@PathVariable Long friendId, Principal principal) {
        try {
            Users sender = userService.getUserByEmaill(principal.getName());
            Users receiver = userService.getUserById(friendId);
            Friendship friendship = friendshipService.sendFriendRequest(sender, receiver);
            return ResponseEntity.ok(friendship);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @PostMapping("/accept/{requestId}")
    public ResponseEntity<?> acceptRequest(@PathVariable Long requestId, Principal principal) {
        try {
            Users receiver = userService.getUserByEmaill(principal.getName());
            Friendship accepted = friendshipService.acceptRequest(requestId, receiver);
            return ResponseEntity.ok(accepted);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName());
            return ResponseEntity.ok(friendshipService.getPendingRequests(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @GetMapping("/pending/count")
    public ResponseEntity<?> getPendingRequestCount(Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName());
            return ResponseEntity.ok(friendshipService.getPendingRequestCount(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @GetMapping("/my")
    public ResponseEntity<?> getMyFriends(Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName());
            return ResponseEntity.ok(friendshipService.getAcceptedFriends(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @GetMapping("/sent")
    public ResponseEntity<?> getSentRequests(Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName());
            return ResponseEntity.ok(friendshipService.getSentRequests(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @PostMapping("/reject/{requestId}")
    public ResponseEntity<?> rejectFriendRequest(@PathVariable Long requestId, Principal principal) {
        try {
            Users receiver = userService.getUserByEmaill(principal.getName());
            friendshipService.rejectRequest(requestId, receiver);
            return ResponseEntity.ok(Collections.singletonMap("message", "Request rejected."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @DeleteMapping("/cancel/{friendId}")
    public ResponseEntity<?> cancelFriendRequest(@PathVariable Long friendId, Principal principal) {
        try {
            Users sender = userService.getUserByEmaill(principal.getName());
            Users receiver = userService.getUserById(friendId);
            friendshipService.cancelRequest(sender, receiver);
            return ResponseEntity.ok(Collections.singletonMap("message", "Request cancelled."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @PostMapping("/block/{userId}")
    public ResponseEntity<?> blockUser(@PathVariable Long userId, Principal principal) {
        try {
            Users blocker = userService.getUserByEmaill(principal.getName());
            Users blocked = userService.getUserById(userId);
            friendshipService.blockUser(blocker, blocked);
            return ResponseEntity.ok(Collections.singletonMap("message", "User blocked."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @DeleteMapping("/unblock/{userId}")
    public ResponseEntity<?> unblockUser(@PathVariable Long userId, Principal principal) {
        try {
            Users blocker = userService.getUserByEmaill(principal.getName());
            Users blocked = userService.getUserById(userId);
            friendshipService.unblockUser(blocker, blocked);
            return ResponseEntity.ok(Collections.singletonMap("message", "User unblocked."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @DeleteMapping("/unfriend/{userId}")
    public ResponseEntity<?> unfriend(@PathVariable Long userId, Principal principal) {
        try {
            Users currentUser = userService.getUserByEmaill(principal.getName());
            Users friend = userService.getUserById(userId);
            friendshipService.unfriend(currentUser, friend);
            return ResponseEntity.ok(Collections.singletonMap("message", "User unfriended."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
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
    @GetMapping("/status/{userId}")
    public ResponseEntity<?> getFriendshipStatus(@PathVariable Long userId, Principal principal) {
        try {
            Users currentUser = userService.getUserByEmaill(principal.getName());
            Users otherUser = userService.getUserById(userId);

            boolean youBlockedThem = friendshipService.didBlock(currentUser, otherUser);
            boolean theyBlockedYou = friendshipService.didBlock(otherUser, currentUser);
            boolean isFriend = friendshipService.areFriends(currentUser, otherUser);

            return ResponseEntity.ok(
                Map.of(
                    "youBlockedThem", youBlockedThem,
                    "theyBlockedYou", theyBlockedYou,
                    "isFriend", isFriend
                )
            );
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }



}
