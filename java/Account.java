import java.util.*;
import java.util.concurrent.atomic.*;

public final class Account {
    private final String id;
    private final AtomicLong balance = new AtomicLong();

    Account(final String id) {
        this.id = Objects.requireNonNull(id);
    }

    public String id() {
        return id;
    }

    public long balance() {
        return balance.get();
    }

    public long setBalance(final long amount) {
        requireNonNegative(amount);
        balance.set(amount);
        return amount;
    }

    public long changeBalance(final long delta) {
        while (true) {
            final long current = balance.get();
            final long updated;
            try {
                updated = Math.addExact(current, delta);
            } catch (final ArithmeticException e) {
                throw new IllegalArgumentException("Balance overflow", e);
            }
            requireNonNegative(updated);
            if (balance.compareAndSet(current, updated)) {
                return updated;
            }
        }
    }

    private static void requireNonNegative(final long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }
    }
}

