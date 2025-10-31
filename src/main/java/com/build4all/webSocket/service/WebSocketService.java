package com.build4all.webSocket.service;

import com.build4all.webSocket.dto.TypingDTO;
import com.build4all.social.domain.ChatMessages;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    private String roomId(Long a, Long b) {
        return (a < b) ? (a + "_" + b) : (b + "_" + a);
    }

    // --- legacy/global rooms ---
    public void broadcastMessage(ChatMessages message) {
        Long senderId = message.getSender().getId();
        Long receiverId = message.getReceiver().getId();
        messagingTemplate.convertAndSend("/topic/chat/" + roomId(senderId, receiverId), message);
    }

    public void broadcastTyping(TypingDTO typingDTO) {
        String id = roomId(typingDTO.getSenderId(), typingDTO.getReceiverId());
        messagingTemplate.convertAndSend("/topic/typing/" + id, typingDTO);
    }

    // --- NEW: scoped by adminId + projectId ---
    public void broadcastMessage(ChatMessages message, Long adminId, Long projectId) {
        Long senderId = message.getSender().getId();
        Long receiverId = message.getReceiver().getId();
        String room = roomId(senderId, receiverId);
        // FE subscribes to /topic/chat/{adminId}/{projectId}/{room}
        messagingTemplate.convertAndSend("/topic/chat/" + adminId + "/" + projectId + "/" + room, message);
    }

    public void broadcastTyping(TypingDTO typingDTO, Long adminId, Long projectId) {
        String room = roomId(typingDTO.getSenderId(), typingDTO.getReceiverId());
        messagingTemplate.convertAndSend("/topic/typing/" + adminId + "/" + projectId + "/" + room, typingDTO);
    }
}
