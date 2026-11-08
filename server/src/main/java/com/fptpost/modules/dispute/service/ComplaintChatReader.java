package com.fptpost.modules.dispute.service;

import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.dispute.dto.response.ComplaintChatHistory;
import com.fptpost.modules.dispute.dto.response.ComplaintChatLine;
import com.fptpost.modules.dispute.enums.ComplaintRole;
import com.fptpost.modules.order.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/** Đọc lịch sử chat của đơn cho kiểm duyệt viên (BR-F-045); lỗi kho chat không chặn việc xử lý khiếu nại. */
@Slf4j
@Component
@RequiredArgsConstructor
class ComplaintChatReader {

    private static final int LIMIT = 500;

    private final MongoTemplate mongo;

    ComplaintChatHistory read(Order order) {
        try {
            List<String> rooms = mongo.find(Query.query(Criteria.where("orderId").is(order.getId())), ChatRoom.class)
                    .stream().map(ChatRoom::getId).toList();
            if (rooms.isEmpty()) return new ComplaintChatHistory(true, List.of());
            Query query = Query.query(Criteria.where("roomId").in(rooms))
                    .with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(LIMIT);
            List<ComplaintChatLine> lines = mongo.find(query, ChatMessage.class).stream()
                    .map(message -> new ComplaintChatLine(role(order, message.getAuthorId()), message.getContent(), message.getCreatedAt()))
                    .toList();
            return new ComplaintChatHistory(true, lines);
        } catch (RuntimeException exception) {
            log.warn("Order chat history unavailable for complaint review of order {}", order.getId());
            return new ComplaintChatHistory(false, List.of());
        }
    }

    private static ComplaintRole role(Order order, Long authorId) {
        if (authorId != null && authorId.equals(order.getSenderId())) return ComplaintRole.SENDER;
        if (authorId != null && authorId.equals(order.getCourierId())) return ComplaintRole.COURIER;
        return ComplaintRole.SYSTEM;
    }
}
