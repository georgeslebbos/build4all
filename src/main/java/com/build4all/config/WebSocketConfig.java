package com.build4all.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Browser (SockJS fallback)
    registry.addEndpoint("/ws-chat")
            .setAllowedOriginPatterns("*")
            .withSockJS();

    // React Native (raw WebSocket)
    registry.addEndpoint("/ws-chat-native")
            .setAllowedOriginPatterns("*"); // tighten in prod
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");            // SUBSCRIBE here
    registry.setApplicationDestinationPrefixes("/app"); // SEND here
  }
}
