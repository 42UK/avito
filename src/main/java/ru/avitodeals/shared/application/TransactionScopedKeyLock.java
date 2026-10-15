package ru.avitodeals.shared.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Component
public class TransactionScopedKeyLock {

	private static final ReentrantLock[] STRIPES = IntStream.range(0, 64)
			.mapToObj(ignored -> new ReentrantLock(true))
			.toArray(ReentrantLock[]::new);

	public void acquire(String key) {
		acquireAll(key);
	}

	public void acquireAll(String... keys) {
		if (keys == null || keys.length == 0 || Arrays.stream(keys).anyMatch(key -> key == null || key.isBlank())) {
			throw new IllegalArgumentException("At least one non-blank lock key is required");
		}
		if (!TransactionSynchronizationManager.isActualTransactionActive()
				|| !TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("A transaction is required to acquire this lock");
		}

		int[] stripeIds = Arrays.stream(keys)
				.mapToInt(key -> Math.floorMod(key.hashCode(), STRIPES.length))
				.distinct()
				.sorted()
				.toArray();
		List<ReentrantLock> acquired = new ArrayList<>(stripeIds.length);
		try {
			for (int stripeId : stripeIds) {
				ReentrantLock lock = STRIPES[stripeId];
				lock.lock();
				acquired.add(lock);
			}
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					for (int index = acquired.size() - 1; index >= 0; index--) {
						acquired.get(index).unlock();
					}
				}
			});
		} catch (RuntimeException exception) {
			for (int index = acquired.size() - 1; index >= 0; index--) {
				acquired.get(index).unlock();
			}
			throw exception;
		}
	}
}
