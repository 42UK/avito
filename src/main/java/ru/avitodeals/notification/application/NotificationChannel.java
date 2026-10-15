package ru.avitodeals.notification.application;

public interface NotificationChannel {

	String channel();

	NotificationResult deliver(NotificationMessage message);
}
