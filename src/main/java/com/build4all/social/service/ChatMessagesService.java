package com.build4all.social.service;

import com.build4all.notifications.service.NotificationsService;
import com.build4all.webSocket.service.WebSocketService;
import com.build4all.social.domain.ChatMessages;
import com.build4all.user.domain.Users;
import com.build4all.social.repository.ChatMessagesRepository;
import com.build4all.social.repository.FriendshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatMessagesService {

    private final ChatMessagesRepository chatRepo;
    private final NotificationsService notificationsService;
    private final FriendshipRepository friendshipRepo;
    private final WebSocketService webSocketService;

    public ChatMessagesService(ChatMessagesRepository chatRepo,
                               NotificationsService notificationsService,
                               FriendshipRepository friendshipRepo,
                               WebSocketService webSocketService) {
        this.chatRepo = chatRepo;
        this.notificationsService = notificationsService;
        this.friendshipRepo = friendshipRepo;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public ChatMessages sendMessage(Users sender, Users receiver, String message) {
        if (!friendshipRepo.areFriends(sender.getId(), receiver.getId())) {
            throw new RuntimeException("You are not friends. Cannot send message.");
        }
        if (friendshipRepo.isBlocked(receiver.getId(), sender.getId())) {
            throw new RuntimeException("This user has blocked you. Cannot send message.");
        }

        ChatMessages chat = new ChatMessages(sender, receiver, message);
        chat.setSentAt(LocalDateTime.now());
        ChatMessages saved = chatRepo.save(chat);

        if (!sender.getId().equals(receiver.getId())) {
            notificationsService.createNotification(
                    receiver,
                    sender.getUsername() + " sent you a message.",
                    "MESSAGE"
            );
        }

        webSocketService.broadcastMessage(saved);
        return saved;
    }

    @Transactional
    public ChatMessages sendMessageWithImage(Users sender, Users receiver, String message, String imageUrl) {
        if (!friendshipRepo.areFriends(sender.getId(), receiver.getId())) {
            throw new RuntimeException("You are not friends. Cannot send message.");
        }
        if (friendshipRepo.isBlocked(receiver.getId(), sender.getId())) {
            throw new RuntimeException("This user has blocked you. Cannot send message.");
        }

        ChatMessages chat = new ChatMessages();
        chat.setSender(sender);
        chat.setReceiver(receiver);
        chat.setMessage(message != null ? message : "");
        chat.setImageUrl(imageUrl);
        chat.setSentAt(LocalDateTime.now());
        ChatMessages saved = chatRepo.save(chat);

        if (!sender.getId().equals(receiver.getId())) {
            notificationsService.createNotification(
                    receiver,
                    sender.getUsername() + " sent you a message.",
                    "MESSAGE"
            );
        }

        webSocketService.broadcastMessage(saved);
        return saved;
    }

    @Transactional
    public String uploadImage(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get("uploads/" + fileName);
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());
            return "/uploads/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image", e);
        }
    }

    @Transactional
    public Long countAllMessagesForUser(Users user) {
        return chatRepo.countBySenderOrReceiver(user);
    }

    @Transactional
    public List<Object[]> countMessagesGroupedByContact(Users user) {
        return chatRepo.countMessagesGroupedByContact(user.getId());
    }

    @Transactional
    public List<Object[]> countUnreadMessagesGroupedByContact(Long userId) {
        return chatRepo.countUnreadMessagesGroupedByContact(userId);
    }

    @Transactional
    public void markMessagesAsRead(Users receiver, Users sender) {
        var unread = chatRepo.findUnreadMessages(receiver.getId(), sender.getId());
        unread.forEach(m -> m.setIsRead(true));
        chatRepo.saveAll(unread);
    }

    @Transactional
    public List<ChatMessages> getConversation(Users user1, Users user2) {
        if (!friendshipRepo.areFriends(user1.getId(), user2.getId())) {
            throw new RuntimeException("You are not friends. Cannot view this conversation.");
        }
        var messages = chatRepo.findConversationBetween(user1.getId(), user2.getId());
        messages.forEach(msg -> {
            msg.getSender().getUsername();
            msg.getReceiver().getUsername();
        });
        return messages;
    }

    @Transactional
    public List<ChatMessages> getMessagesByUser(Users user) {
        return chatRepo.findBySenderOrReceiverOrderByMessageDatetimeDesc(user, user);
    }

    @Transactional
    public boolean deleteMessageByIdAndUser(Long messageId, Users user) {
        var message = chatRepo.findById(messageId).orElse(null);
        if (message == null || !message.getSender().getId().equals(user.getId())) return false;
        chatRepo.delete(message);
        return true;
    }

    @Transactional
    public void markSingleMessageAsRead(Users currentUser, Long messageId) {
        var message = chatRepo.findById(messageId)
            .orElseThrow(() -> new RuntimeException("Message not found"));
        if (!message.getReceiver().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Unauthorized to mark this message as read");
        }
        message.setIsRead(true);
        chatRepo.save(message);
    }
    
   
    
}
