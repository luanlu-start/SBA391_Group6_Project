package com.fptpost.modules.chat.repository;

import com.fptpost.modules.chat.model.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    Optional<ChatRoom> findByOrderIdAndSenderIdAndCourierId(
            Long orderId,
            Long senderId,
            Long courierId
    );
}