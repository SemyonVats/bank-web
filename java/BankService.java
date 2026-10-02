import java.util.*;
import java.util.concurrent.*;

public final class BankService {
    private final ConcurrentMap<String, Person> people = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Account> accounts = new ConcurrentHashMap<>();

    public Person createPerson(final String firstName, final String lastName, final String passport) {
        final Person requested = new Person(firstName, lastName, passport);
        return people.compute(requested.passport(), (key, existing) -> {
            if (existing != null && !existing.equals(requested)) {
                throw new IllegalArgumentException("This passport belongs to another person");
            }
            return requested;
        });
    }

    public Person getPerson(final String passport) {
        return people.get(requireText(passport, "Passport"));
    }

    public Account createAccount(final String passport, final String subId) {
        final String normalizedPassport = requireText(passport, "Passport");
        if (!people.containsKey(normalizedPassport)) {
            throw new IllegalArgumentException("Create the person before creating an account");
        }
        final String id = accountId(normalizedPassport, subId);
        return accounts.computeIfAbsent(id, Account::new);
    }

    public Account getAccount(final String passport, final String subId) {
        return accounts.get(accountId(passport, subId));
    }

    public List<Account> getAccounts(final String passport) {
        final String prefix = requireText(passport, "Passport") + ":";
        return accounts.values().stream()
                .filter(account -> account.id().startsWith(prefix))
                .sorted(Comparator.comparing(Account::id))
                .toList();
    }

    private static String accountId(final String passport, final String subId) {
        final String normalizedPassport = requireText(passport, "Passport");
        final String normalizedSubId = requireText(subId, "Account id");
        if (normalizedPassport.contains(":") || normalizedSubId.contains(":")) {
            throw new IllegalArgumentException("Passport and account id cannot contain ':'");
        }
        return normalizedPassport + ":" + normalizedSubId;
    }

    private static String requireText(final String value, final String field) {
        final String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return normalized;
    }
}

