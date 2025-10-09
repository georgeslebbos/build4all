package com.build4all.social.service;

import com.build4all.notifications.service.NotificationsService;
import com.build4all.social.domain.Friendship;
import com.build4all.user.domain.Users;
import com.build4all.social.repository.FriendshipRepository;
import com.build4all.notifications.repository.NotificationTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepo;
    private final NotificationsService notificationsService;
    private final NotificationTypeRepository notificationTypeRepo;

    public FriendshipService(FriendshipRepository friendshipRepo,
                             NotificationsService notificationsService,
                             NotificationTypeRepository notificationTypeRepo) {
        this.friendshipRepo = friendshipRepo;
        this.notificationsService = notificationsService;
        this.notificationTypeRepo = notificationTypeRepo;
    }

    public Friendship sendFriendRequest(Users sender, Users receiver) {
        if (sender.equals(receiver)) {
            throw new RuntimeException("You cannot add yourself.");
        }

        boolean exists = friendshipRepo.existsByUserIdAndFriendId(sender.getId(), receiver.getId());
        if (exists) {
            throw new RuntimeException("Friend request already exists.");
        }

        Friendship friendship = new Friendship();
        friendship.setUser(sender);
        friendship.setFriend(receiver);
        friendship.setStatus("PENDING");

        Friendship saved = friendshipRepo.save(friendship);

        notificationsService.createNotification(
            receiver,
            sender.getUsername() + " sent you a friend request.",
            "FRIEND_REQUEST_SENT"
        );

        return saved;
    }

    public Friendship acceptRequest(Long requestId, Users receiver) {
        Friendship request = friendshipRepo.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getFriend().getId().equals(receiver.getId())) {
            throw new RuntimeException("Not authorized to accept this request");
        }

        request.setStatus("ACCEPTED");
        Friendship accepted = friendshipRepo.save(request);

        notificationsService.createNotification(
            request.getUser(),
            receiver.getUsername() + " accepted your friend request.",
            "FRIEND_REQUEST_ACCEPTED"
        );

        return accepted;
    }

    public void rejectRequest(Long requestId, Users receiver) {
        Friendship request = friendshipRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getFriend().getId().equals(receiver.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        request.setStatus("REJECTED");
        friendshipRepo.save(request);

        notificationsService.createNotification(
            request.getUser(),
            receiver.getUsername() + " rejected your friend request.",
            "FRIEND_REQUEST_REJECTED"
        );
    }

    public void cancelRequest(Users sender, Users receiver) {
        Friendship request = friendshipRepo.findByUserIdAndFriendIdAndStatus(
                sender.getId(), receiver.getId(), "PENDING"
        ).orElseThrow(() -> new RuntimeException("Request not found"));

        friendshipRepo.delete(request);

        notificationsService.createNotification(
            receiver,
            sender.getUsername() + " canceled the friend request.",
            "FRIEND_REQUEST_CANCELLED"
        );
    }

    public void blockUser(Users blocker, Users blocked) {
        Friendship friendship = new Friendship();
        friendship.setUser(blocker);
        friendship.setFriend(blocked);
        friendship.setStatus("BLOCKED");

        friendshipRepo.save(friendship);

        notificationsService.createNotification(
            blocked,
            "You have been blocked by " + blocker.getUsername(),
            "FRIEND_BLOCKED"
        );
    }

    public void unblockUser(Users blocker, Users blocked) {
        friendshipRepo.findByUserIdAndFriendIdAndStatus(blocker.getId(), blocked.getId(), "BLOCKED")
            .ifPresent(friendshipRepo::delete);
    }

    public void unfriend(Users user1, Users user2) {
        friendshipRepo.findAcceptedFriendship(user1.getId(), user2.getId())
            .ifPresent(friendship -> {
                friendshipRepo.delete(friendship);

                Users other = friendship.getUser().equals(user1) ? friendship.getFriend() : friendship.getUser();

                notificationsService.createNotification(
                    other,
                    user1.getUsername() + " removed you from their friends.",
                    "FRIEND_REMOVED"
                );
            });
    }

    public List<Friendship> getPendingRequests(Users user) {
        return friendshipRepo.findByFriendIdAndStatus(user.getId(), "PENDING");
    }

    public int getPendingRequestCount(Users user) {
        return friendshipRepo.findByFriendIdAndStatus(user.getId(), "PENDING").size();
    }

    public List<Users> getAcceptedFriends(Users user) {
        List<Friendship> friendships = friendshipRepo.findAcceptedFriendships(user.getId());

        return friendships.stream()
                .map(f -> f.getUser().getId().equals(user.getId()) ? f.getFriend() : f.getUser())
                .filter(friend ->
                    friend.getStatus() != null &&
                    "ACTIVE".equals(friend.getStatus().getName()) &&
                    (friend.getIsPublicProfile() || areFriends(user, friend))
                )
                .collect(Collectors.toList());
    }

    public List<Users> getSentRequests(Users user) {
        List<Friendship> sentRequests = friendshipRepo.findByUserIdAndStatus(user.getId(), "PENDING");

        return sentRequests.stream()
                .map(Friendship::getFriend)
                .collect(Collectors.toList());
    }

    public boolean isBlocked(Users currentUser, Users otherUser) {
        return friendshipRepo.findByUserIdAndFriendIdAndStatus(currentUser.getId(), otherUser.getId(), "BLOCKED").isPresent();
    }

    public boolean areFriends(Users user1, Users user2) {
        if (user1 == null || user2 == null || user1.getId() == null || user2.getId() == null) {
            return false;
        }
        return friendshipRepo.findAcceptedFriendship(user1.getId(), user2.getId()).isPresent();
    }

    public boolean didBlock(Users blocker, Users blocked) {
        return friendshipRepo.findByUserIdAndFriendIdAndStatus(blocker.getId(), blocked.getId(), "BLOCKED").isPresent();
    }

    public boolean hasPendingRequestBetween(Users currentUser, Users otherUser) {
        return friendshipRepo.findByUserIdAndFriendIdAndStatus(currentUser.getId(), otherUser.getId(), "PENDING").isPresent()
            || friendshipRepo.findByUserIdAndFriendIdAndStatus(otherUser.getId(), currentUser.getId(), "PENDING").isPresent();
    }
}
