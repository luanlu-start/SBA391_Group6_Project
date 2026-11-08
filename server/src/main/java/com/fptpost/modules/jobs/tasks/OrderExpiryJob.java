package com.fptpost.modules.jobs.tasks;

import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.jobs.repository.OrderMaintenanceRepository;
import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobResult;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** BR-R-020: đơn đang mở quá OPEN_DEFAULT giờ mà chưa có người giao chuyển Hết hạn và báo người gửi. */
@Component
@RequiredArgsConstructor
public class OrderExpiryJob implements BackgroundJob {

    private static final int BATCH = 200;

    private final OrderMaintenanceRepository orders;
    private final ParameterService parameterService;
    private final UserRepository users;
    private final NotificationPublisher notifications;

    @Override
    public String name() {
        return "order-expiry";
    }

    @Override
    public String title() {
        return "Hết hạn đơn chưa có người giao";
    }

    @Override
    public String description() {
        return "Đơn đang mở quá OPEN_DEFAULT giờ chưa có người giao chuyển Hết hạn; chào giá và lời mời còn chờ cũng hết hạn.";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(10);
    }

    @Override
    public JobResult execute(Instant now) {
        long openHours = parameterService.getSnapshot().integer("OPEN_DEFAULT");
        List<Order> stale = orders.findStaleOpenOrders(now.minus(Duration.ofHours(openHours)), PageRequest.of(0, BATCH));
        if (stale.isEmpty()) return new JobResult(0, "Không có đơn quá hạn mở");
        List<Long> ids = stale.stream().map(Order::getId).toList();
        int expired = orders.expireOrders(ids, now);
        orders.expireOffersOfOrders(ids, now);
        orders.expireInvitationsOfOrders(ids, now);
        for (Order order : stale) {
            users.findById(order.getSenderId()).filter(user -> !user.isDeleted()).map(User::getPublicId).ifPresent(sender ->
                    notifications.publish(sender, NotificationCategory.ORDER, "order-expired:" + order.getId(),
                            "Đơn hàng đã hết hạn", "Đơn " + order.getOrderCode() + " không có người giao nhận sau "
                                    + openHours + " giờ nên đã hết hạn. Bạn có thể tạo lại đơn.", "/sender/orders/" + order.getId()));
        }
        return new JobResult(expired, "Đã chuyển " + expired + " đơn sang Hết hạn");
    }
}
