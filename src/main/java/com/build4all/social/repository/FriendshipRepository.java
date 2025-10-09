package com.build4all.social.repository;

import com.build4all.social.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    Optional<Friendship> findByUserIdAndFriendId(Long userId, Long friendId);

    List<Friendship> findByFriendIdAndStatus(Long friendId, String status);

    @Query("""
        SELECT COUNT(f) > 0 FROM Friendship f 
        WHERE ((f.user.id = :userId1 AND f.friend.id = :userId2) OR 
               (f.user.id = :userId2 AND f.friend.id = :userId1))
          AND f.status = 'ACCEPTED'
    """)
    boolean areFriends(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
 // Get list of accepted friends for a user
    @Query("""
        SELECT f FROM Friendship f 
        WHERE (f.user.id = :userId OR f.friend.id = :userId)
          AND f.status = 'ACCEPTED'
    """)
    List<Friendship> findAcceptedFriendships(@Param("userId") Long userId);

    // Cancel a sent request
    Optional<Friendship> findByUserIdAndFriendIdAndStatus(Long userId, Long friendId, String status);

 // Check if user has blocked the other
    @Query("""
        SELECT COUNT(f) > 0 FROM Friendship f 
        WHERE f.user.id = :blockerId AND f.friend.id = :blockedId 
          AND f.status = 'BLOCKED'
    """)
    boolean isBlocked(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    // Get accepted friendship record between 2 users (for unfriend)
    @Query("""
        SELECT f FROM Friendship f 
        WHERE ((f.user.id = :id1 AND f.friend.id = :id2) OR 
               (f.user.id = :id2 AND f.friend.id = :id1))
          AND f.status = 'ACCEPTED'
    """)
    Optional<Friendship> findAcceptedFriendship(@Param("id1") Long id1, @Param("id2") Long id2);

    List<Friendship> findByUserIdAndStatus(Long userId, String status);



}
