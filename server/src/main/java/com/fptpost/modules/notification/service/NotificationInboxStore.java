package com.fptpost.modules.notification.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.notification.dto.response.NotificationResponse;
import com.fptpost.modules.notification.exception.NotificationErrorCode;
import com.fptpost.modules.notification.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class NotificationInboxStore {
    private final MongoTemplate mongo;
    private final Clock clock;

    public NotificationResponse append(NotificationEvent event) {
        var query = Query.query(Criteria.where("_id").is(event.getId().toString()));
        var update = new Update().setOnInsert("userId", event.getRecipient().toString()).setOnInsert("category", event.getCategory())
                .setOnInsert("title", event.getTitle()).setOnInsert("body", event.getBody()).setOnInsert("relativeLink", event.getRelativeLink())
                .setOnInsert("at", event.getAt());
        var saved = mongo.findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true), InboxNotification.class);
        if (saved == null || !saved.getUserId().equals(event.getRecipient().toString())) throw new IllegalStateException("Notification projection mismatch");
        return response(saved);
    }

    public PageResponse<NotificationResponse> list(UUID owner, boolean unreadOnly, int page, int size) {
        try {
            var criteria = Criteria.where("userId").is(owner.toString());
            if (unreadOnly) criteria.and("readAt").is(null);
            var query = Query.query(criteria);
            long count = mongo.count(query, InboxNotification.class);
            var content = mongo.find(query.with(PageRequest.of(page, size, Sort.by(Sort.Order.desc("at"), Sort.Order.desc("_id")))), InboxNotification.class)
                    .stream().map(this::response).toList();
            return PageResponse.from(new PageImpl<>(content, PageRequest.of(page, size), count));
        } catch (DataAccessException failure) { throw unavailable(); }
    }

    public long unreadCount(UUID owner) {
        try { return mongo.count(Query.query(Criteria.where("userId").is(owner.toString()).and("readAt").is(null)), InboxNotification.class); }
        catch (DataAccessException failure) { throw unavailable(); }
    }

    public NotificationResponse read(UUID owner, UUID id) {
        try {
            var filter = Criteria.where("_id").is(id.toString()).and("userId").is(owner.toString());
            var updated = mongo.findAndModify(Query.query(filter.and("readAt").is(null)), new Update().set("readAt", clock.instant()),
                    FindAndModifyOptions.options().returnNew(true), InboxNotification.class);
            if (updated == null) updated = mongo.findOne(Query.query(Criteria.where("_id").is(id.toString()).and("userId").is(owner.toString())), InboxNotification.class);
            if (updated == null) throw new AppException(NotificationErrorCode.NOT_FOUND);
            return response(updated);
        } catch (DataAccessException failure) { throw unavailable(); }
    }

    public long readAll(UUID owner) {
        try {
            return mongo.updateMulti(Query.query(Criteria.where("userId").is(owner.toString()).and("readAt").is(null)),
                    new Update().set("readAt", clock.instant()), InboxNotification.class).getModifiedCount();
        } catch (DataAccessException failure) { throw unavailable(); }
    }

    private NotificationResponse response(InboxNotification value) {
        return new NotificationResponse(UUID.fromString(value.getId()), value.getCategory(), value.getTitle(), value.getBody(), value.getRelativeLink(), value.getAt(), value.getReadAt());
    }
    private AppException unavailable() { return new AppException(NotificationErrorCode.STORE_UNAVAILABLE); }
}
