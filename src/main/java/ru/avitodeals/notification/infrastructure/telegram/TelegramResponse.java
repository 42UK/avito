package ru.avitodeals.notification.infrastructure.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramResponse(boolean ok, @JsonProperty("error_code") Integer errorCode) {
}
