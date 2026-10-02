import java.util.*;

public record Person(String firstName, String lastName, String passport) {
    public Person {
        firstName = requireText(firstName, "First name");
        lastName = requireText(lastName, "Last name");
        passport = requireText(passport, "Passport");
    }

    private static String requireText(final String value, final String field) {
        final String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return normalized;
    }
}

