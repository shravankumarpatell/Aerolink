package com.aerolink.ride.messaging;

import com.aerolink.ride.config.RabbitMQConfig;
import com.aerolink.ride.dto.event.RideEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishCancellation(RideEvent event) {
        log.info("Publishing cancellation event for ride: {}", event.getRideRequestId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.CANCELLATION_ROUTING_KEY,
                event);
    }

    public void publishNotification(RideEvent event) {
        log.info("Publishing notification event: {} for ride: {}", event.getEventType(), event.getRideRequestId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                event);
    }
}
