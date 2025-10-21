package com.build4all.social.dto;

import java.time.format.DateTimeFormatter;
import com.build4all.social.domain.ChatMessages;

public class ChatMessageDto {
    private boolean isRead;

    private Long id;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String message;
    private String sentAt;
    private boolean isMine;
    private String imageUrl;

    // <<-- IMPORTANT for inbound WebSocket mapping
    public ChatMessageDto() {}

    public ChatMessageDto(ChatMessages chat, Long currentUserId) {
        this.id = chat.getId();
        this.senderId = chat.getSender().getId();
        this.senderName = chat.getSender().getFirstName() + " " + chat.getSender().getLastName();
        this.receiverId = chat.getReceiver().getId();
        this.message = chat.getMessage();
        this.imageUrl = chat.getImageUrl();

        this.isRead = Boolean.TRUE.equals(chat.getIsRead());

        if (chat.getSentAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            this.sentAt = chat.getSentAt().format(formatter);
        } else {
            this.sentAt = null;
        }

        this.isMine = this.senderId.equals(currentUserId);
    }

    public boolean isMine() { return isMine; }
    public void setIsMine(boolean isMine) { this.isMine = isMine; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
