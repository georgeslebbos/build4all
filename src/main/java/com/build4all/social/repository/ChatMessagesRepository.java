package com.build4all.social.repository;

import com.build4all.social.domain.ChatMessages;
import com.build4all.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessagesRepository extends JpaRepository<ChatMessages, Long> {

    // ✅ Get all messages in the conversation between two users
    @Query("SELECT m FROM ChatMessages m WHERE " +
           "(m.sender.id = :id1 AND m.receiver.id = :id2) OR " +
           "(m.sender.id = :id2 AND m.receiver.id = :id1) " +
           "ORDER BY m.sentAt ASC")
    List<ChatMessages> findConversationBetween(@Param("id1") Long id1, @Param("id2") Long id2);

    // ✅ Get all messages where user is sender or receiver
    List<ChatMessages> findBySenderOrReceiver(Users sender, Users receiver);

    // ✅ Get conversation ordered by date between two users
    List<ChatMessages> findBySenderAndReceiverOrReceiverAndSenderOrderByMessageDatetimeAsc(
            Users user1, Users user2, Users user3, Users user4);

    // ✅ Get all messages involving the user ordered by date (latest first)
    List<ChatMessages> findBySenderOrReceiverOrderByMessageDatetimeDesc(Users user, Users user2);

    // ✅ Count all messages sent or received by a user
    @Query("SELECT COUNT(m) FROM ChatMessages m WHERE m.sender = :user OR m.receiver = :user")
    Long countBySenderOrReceiver(@Param("user") Users user);

    // ✅ Count total messages grouped by each contact
    @Query("""
        SELECT 
            CASE 
                WHEN m.sender.id = :userId THEN m.receiver.id 
                ELSE m.sender.id 
            END AS contactId,
            COUNT(m) 
        FROM ChatMessages m 
        WHERE m.sender.id = :userId OR m.receiver.id = :userId 
        GROUP BY contactId
    """)
    List<Object[]> countMessagesGroupedByContact(@Param("userId") Long userId);

    // ✅ Count unread messages grouped by contact for the current user
    @Query("""
    	    SELECT 
    	        m.sender.id AS contactId,
    	        COUNT(m)
    	    FROM ChatMessages m
    	    WHERE m.receiver.id = :userId AND m.isRead = false
    	    GROUP BY m.sender.id
    	""")
    	List<Object[]> countUnreadMessagesGroupedByContact(@Param("userId") Long userId);


    // ✅ Find all unread messages sent from a specific sender to a receiver
    @Query("""
        SELECT m FROM ChatMessages m
        WHERE m.receiver.id = :receiverId AND m.sender.id = :senderId AND m.isRead = false
    """)
    List<ChatMessages> findUnreadMessages(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);
    
    @Query("SELECT m FROM ChatMessages m JOIN FETCH m.sender WHERE m.sender = :user OR m.receiver = :user")
    List<ChatMessages> findBySenderOrReceiverWithUser(@Param("user") Users user);

}
