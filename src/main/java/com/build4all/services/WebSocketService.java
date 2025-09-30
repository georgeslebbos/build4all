package com.build4all.services;

import com.build4all.entities.ChatMessages;
import com.build4all.dto.TypingDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastMessage(ChatMessages message) {
        Long senderId = message.getSender().getId();
        Long receiverId = message.getReceiver().getId();
        String roomId = getRoomId(senderId, receiverId);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
    }

    public void broadcastTyping(TypingDTO typingDTO) {
        String roomId = getRoomId(typingDTO.getSenderId(), typingDTO.getReceiverId());
        messagingTemplate.convertAndSend("/topic/typing/" + roomId, typingDTO);
    }

    private String getRoomId(Long user1, Long user2) {
        return user1 < user2 ? user1 + "_" + user2 : user2 + "_" + user1;
    }
}
