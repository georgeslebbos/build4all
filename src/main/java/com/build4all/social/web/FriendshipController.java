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

    @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful")})
    @PostMapping("/add/{friendId}")
    public ResponseEntity<?> sendFriendRequest(@PathVariable Long friendId,
                                               @RequestParam Long adminId,
                                               @RequestParam Long projectId,
                                               Principal principal) {
        try {
            Users sender   = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Users receiver = userService.getUserById(friendId, adminId, projectId);
            Friendship friendship = friendshipService.sendFriendRequest(sender, receiver);
            return ResponseEntity.ok(friendship);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @PostMapping("/accept/{requestId}")
    public ResponseEntity<?> acceptRequest(@PathVariable Long requestId,
                                           @RequestParam Long adminId,
                                           @RequestParam Long projectId,
                                           Principal principal) {
        try {
            Users receiver = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Friendship accepted = friendshipService.acceptRequest(requestId, receiver);
            return ResponseEntity.ok(accepted);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(@RequestParam Long adminId,
                                                @RequestParam Long projectId,
                                                Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            return ResponseEntity.ok(friendshipService.getPendingRequests(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @GetMapping("/pending/count")
    public ResponseEntity<?> getPendingRequestCount(@RequestParam Long adminId,
                                                    @RequestParam Long projectId,
                                                    Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            return ResponseEntity.ok(friendshipService.getPendingRequestCount(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyFriends(@RequestParam Long adminId,
                                          @RequestParam Long projectId,
                                          Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            return ResponseEntity.ok(friendshipService.getAcceptedFriends(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @GetMapping("/sent")
    public ResponseEntity<?> getSentRequests(@RequestParam Long adminId,
                                             @RequestParam Long projectId,
                                             Principal principal) {
        try {
            Users user = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            return ResponseEntity.ok(friendshipService.getSentRequests(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @PostMapping("/reject/{requestId}")
    public ResponseEntity<?> rejectFriendRequest(@PathVariable Long requestId,
                                                 @RequestParam Long adminId,
                                                 @RequestParam Long projectId,
                                                 Principal principal) {
        try {
            Users receiver = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            friendshipService.rejectRequest(requestId, receiver);
            return ResponseEntity.ok(Collections.singletonMap("message", "Request rejected."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/cancel/{friendId}")
    public ResponseEntity<?> cancelFriendRequest(@PathVariable Long friendId,
                                                 @RequestParam Long adminId,
                                                 @RequestParam Long projectId,
                                                 Principal principal) {
        try {
            Users sender   = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Users receiver = userService.getUserById(friendId, adminId, projectId);
            friendshipService.cancelRequest(sender, receiver);
            return ResponseEntity.ok(Collections.singletonMap("message", "Request cancelled."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @PostMapping("/block/{userId}")
    public ResponseEntity<?> blockUser(@PathVariable Long userId,
                                       @RequestParam Long adminId,
                                       @RequestParam Long projectId,
                                       Principal principal) {
        try {
            Users blocker = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Users blocked = userService.getUserById(userId, adminId, projectId);
            friendshipService.blockUser(blocker, blocked);
            return ResponseEntity.ok(Collections.singletonMap("message", "User blocked."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/unblock/{userId}")
    public ResponseEntity<?> unblockUser(@PathVariable Long userId,
                                         @RequestParam Long adminId,
                                         @RequestParam Long projectId,
                                         Principal principal) {
        try {
            Users blocker = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Users blocked = userService.getUserById(userId, adminId, projectId);
            friendshipService.unblockUser(blocker, blocked);
            return ResponseEntity.ok(Collections.singletonMap("message", "User unblocked."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/unfriend/{userId}")
    public ResponseEntity<?> unfriend(@PathVariable Long userId,
                                      @RequestParam Long adminId,
                                      @RequestParam Long projectId,
                                      Principal principal) {
        try {
            Users currentUser = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Users friend      = userService.getUserById(userId, adminId, projectId);
            friendshipService.unfriend(currentUser, friend);
            return ResponseEntity.ok(Collections.singletonMap("message", "User unfriended."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        }
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<?> getFriendshipStatus(@PathVariable Long userId,
                                                 @RequestParam Long adminId,
                                                 @RequestParam Long projectId,
                                                 Principal principal) {
        try {
            Users currentUser = userService.getUserByEmaill(principal.getName(), adminId, projectId);
            Users otherUser   = userService.getUserById(userId, adminId, projectId);

            boolean youBlockedThem = friendshipService.didBlock(currentUser, otherUser);
            boolean theyBlockedYou = friendshipService.didBlock(otherUser, currentUser);
            boolean isFriend       = friendshipService.areFriends(currentUser, otherUser);

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
