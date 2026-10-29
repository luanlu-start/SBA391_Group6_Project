package com.fptpost.modules.notification;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.model.*;
import com.fptpost.modules.notification.service.NotificationInboxStore;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationInboxStoreTest {
    private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final NotificationInboxStore inbox = new NotificationInboxStore(mongo, Clock.fixed(now, ZoneOffset.UTC));

    @Test void projectionReplayUsesSameIdAndNeverOverwritesReadState() {
        var event = NotificationEvent.builder().id(id).recipient(owner).category(NotificationCategory.KYC).title("Kết quả").body("Đã duyệt").at(now).build();
        var existing = InboxNotification.builder().id(id.toString()).userId(owner.toString()).category(NotificationCategory.KYC)
                .title("Kết quả").body("Đã duyệt").at(now).readAt(now.minusSeconds(1)).build();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InboxNotification.class))).thenReturn(existing);
        assertThat(inbox.append(event).readAt()).isEqualTo(existing.getReadAt());
        var query = ArgumentCaptor.forClass(Query.class); var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(InboxNotification.class));
        assertThat(query.getValue().getQueryObject().get("_id")).isEqualTo(id.toString());
        assertThat(update.getValue().getUpdateObject()).containsOnlyKeys("$setOnInsert");
        assertThat((org.bson.Document) update.getValue().getUpdateObject().get("$setOnInsert")).doesNotContainKey("readAt");
    }

    @Test void otherUsersNotificationCannotBeMarkedReadOrDiscovered() {
        assertThatThrownBy(() -> inbox.read(owner, id)).isInstanceOf(AppException.class)
                .extracting(value -> ((AppException) value).getErrorCode().getCode()).isEqualTo("NOTIFICATION_NOT_FOUND");
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(query.capture(), eq(InboxNotification.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("userId", owner.toString()).containsEntry("_id", id.toString());
    }

    @Test void unreadQueriesAlwaysScopeToCurrentUser() {
        when(mongo.count(any(Query.class), eq(InboxNotification.class))).thenReturn(4L);
        assertThat(inbox.unreadCount(owner)).isEqualTo(4);
        var query = ArgumentCaptor.forClass(Query.class); verify(mongo).count(query.capture(), eq(InboxNotification.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("userId", owner.toString()).containsEntry("readAt", null);
    }

    @Test void unavailableMongoIsNotReportedAsEmptyInbox() {
        when(mongo.count(any(Query.class), eq(InboxNotification.class))).thenThrow(new DataAccessResourceFailureException("internal connection detail"));
        assertThatThrownBy(() -> inbox.list(owner, false, 0, 20)).isInstanceOf(AppException.class)
                .extracting(value -> ((AppException) value).getErrorCode().getCode()).isEqualTo("NOTIFICATION_STORE_UNAVAILABLE");
    }
}
