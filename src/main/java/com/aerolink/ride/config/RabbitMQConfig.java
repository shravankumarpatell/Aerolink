package com.aerolink.ride.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "ride.events";
    public static final String CANCELLATION_QUEUE = "ride.cancellation";
    public static final String NOTIFICATION_QUEUE = "ride.notification";
    public static final String CANCELLATION_ROUTING_KEY = "ride.event.cancelled";
    public static final String NOTIFICATION_ROUTING_KEY = "ride.event.notification";

    @Bean
    public TopicExchange rideExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue cancellationQueue() {
        return QueueBuilder.durable(CANCELLATION_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding cancellationBinding(Queue cancellationQueue, TopicExchange rideExchange) {
        return BindingBuilder.bind(cancellationQueue).to(rideExchange).with(CANCELLATION_ROUTING_KEY);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange rideExchange) {
        return BindingBuilder.bind(notificationQueue).to(rideExchange).with("ride.event.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
