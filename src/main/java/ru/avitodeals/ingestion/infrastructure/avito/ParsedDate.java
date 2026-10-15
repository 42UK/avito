package ru.avitodeals.ingestion.infrastructure.avito;

import java.time.Instant;
import java.util.Objects;

public record ParsedDate(Instant instant, DateAccuracy accuracy) {

	public ParsedDate {
		Objects.requireNonNull(instant, "instant");
		Objects.requireNonNull(accuracy, "accuracy");
	}
}
