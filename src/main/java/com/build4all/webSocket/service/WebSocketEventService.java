package com.build4all.webSocket.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WebSocketEventService {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketEventService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // --- simple legacy channels (strings only) stay fine
    public void sendActivityDeleted(String activityId) {
        messagingTemplate.convertAndSend("/topic/activityDeleted", activityId == null ? "" : activityId);
    }

    public void sendActivityUpdated(String activityId) {
        messagingTemplate.convertAndSend("/topic/activityUpdated", activityId == null ? "" : activityId);
    }

    public void sendPostCreated(String postId) {
        messagingTemplate.convertAndSend("/topic/postCreated", postId == null ? "" : postId);
    }

    public void sendPostDeleted(String postId) {
        messagingTemplate.convertAndSend("/topic/postDeleted", postId == null ? "" : postId);
    }

    public void sendPostCreated(Long postId) { sendPostCreated(String.valueOf(postId)); }
    public void sendPostDeleted(Long postId) { sendPostDeleted(String.valueOf(postId)); }

    private Map<String, Object> envelope(String domain, String action, long resourceId, Map<String, Object> data) {
        Map<String, Object> env = new HashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("domain", domain);
        env.put("action", action);
        env.put("businessId", 0);
        env.put("resourceId", resourceId);
        env.put("ts", Instant.now().toString());
        if (data != null && !data.isEmpty()) env.put("data", data);
        return env;
    }

    /** Post updated */
    public void sendPostUpdated(Long postId, Map<String, Object> changes) {
        messagingTemplate.convertAndSend("/topic/posts",
            envelope("post", "updated", postId == null ? 0L : postId, safe(changes))
        );
    }

    /** Comment added stream per post */
    public void sendCommentAdded(Long postId, Map<String, Object> commentDto) {
        messagingTemplate.convertAndSend("/topic/comments." + (postId == null ? 0L : postId),
            envelope("comment", "created",
                (commentDto != null && commentDto.get("id") instanceof Number)
                    ? ((Number) commentDto.get("id")).longValue() : 0L,
                mapOf("postId", postId, "comment", safe(commentDto)))
        );
    }

    /** Comment deleted stream per post */
    public void sendCommentDeleted(Long postId, Long commentId) {
        messagingTemplate.convertAndSend("/topic/comments." + (postId == null ? 0L : postId),
            envelope("comment", "deleted", commentId == null ? 0L : commentId,
                mapOf("postId", postId, "commentId", commentId))
        );
    }

    /** Like toggled */
    public void sendLikeChanged(Long postId, boolean liked, Long likerId) {
        messagingTemplate.convertAndSend("/topic/posts",
            envelope("like", "updated", postId == null ? 0L : postId,
                mapOf("postId", postId, "liked", liked, "likerId", likerId))
        );
    }

    /** Per-user unread badge bump */
    public void sendUnreadBumped(Long userId) {
        messagingTemplate.convertAndSend("/user/" + (userId == null ? 0L : userId) + "/queue/notifications",
            envelope("notification", "updated", userId == null ? 0L : userId,
                mapOf("userId", userId))
        );
    }

    /** Chat message */
    public void sendChatMessage(String roomId, Object messageDto) {
        messagingTemplate.convertAndSend("/topic/chat/" + (roomId == null ? "" : roomId),
            messageDto == null ? new HashMap<>() : messageDto);
    }

    /** Typing */
    public void sendTyping(String roomId, Object typingDto) {
        messagingTemplate.convertAndSend("/topic/typing/" + (roomId == null ? "" : roomId),
            typingDto == null ? new HashMap<>() : typingDto);
    }

    // --- helpers (null-safe map builders) ---

    private Map<String, Object> safe(Map<String, Object> in) {
        if (in == null) return new HashMap<>();
        Map<String, Object> out = new HashMap<>();
        in.forEach((k, v) -> { if (v != null) out.put(k, v); });
        return out;
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = String.valueOf(kv[i]);
            Object v = kv[i + 1];
            if (v != null) m.put(k, v);
        }
        return m;
    }
}
