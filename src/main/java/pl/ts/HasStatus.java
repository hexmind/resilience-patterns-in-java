package pl.ts;

import feign.FeignException;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class HasStatus implements Predicate<Throwable> {

    static final HasStatus TOO_MANY_REQUESTS = new HasStatus(s -> s == 429);
    static final HasStatus ANY_SERVER_ERROR = new HasStatus(s -> s >= 500);

    private final IntPredicate expected;

    public HasStatus(IntPredicate expected) {
        this.expected = expected;
    }

    @Override
    public boolean test(Throwable e) {
        if (e instanceof FeignException) {
            return expected.test(((FeignException) e).status());
        } else {
            return false;
        }
    }
}
