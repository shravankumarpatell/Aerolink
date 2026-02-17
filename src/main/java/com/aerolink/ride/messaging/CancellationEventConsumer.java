package com.aerolink.ride.messaging;

import com.aerolink.ride.config.RabbitMQConfig;
import com.aerolink.ride.dto.event.RideEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancellationEventConsumer {

    /**
     * Processes cancellation events from the queue.
     * In a production system, this would trigger notifications, refund processing,
     * etc.
     */
    @RabbitListener(queues = RabbitMQConfig.CANCELLATION_QUEUE)
    public void handleCancellation(RideEvent event) {
        log.info("Processing cancellation event: rideId={}, poolId={}, reason={}",
                event.getRideRequestId(), event.getRidePoolId(), event.getReason());

        // In production: trigger notification to affected riders, process refunds, etc.
        log.info("Cancellation processed successfully for ride: {}", event.getRideRequestId());
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotification(RideEvent event) {
        log.info("Processing notification event: type={}, rideId={}",
                event.getEventType(), event.getRideRequestId());

        // In production: send push notifications, SMS, emails
        log.info("Notification sent for event: {}", event.getEventType());
    }
}
