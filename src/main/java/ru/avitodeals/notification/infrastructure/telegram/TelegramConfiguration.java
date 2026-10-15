package ru.avitodeals.notification.infrastructure.telegram;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TelegramNotificationProperties.class)
public class TelegramConfiguration {
}
