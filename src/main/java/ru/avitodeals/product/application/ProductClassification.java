package ru.avitodeals.product.application;

import ru.avitodeals.product.domain.MatchStatus;

public record ProductClassification(MatchStatus status, ProductNormalization normalization) {
}
