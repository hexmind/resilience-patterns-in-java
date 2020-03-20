package pl.ts.common;

import io.vavr.collection.Stream;
import io.vavr.control.Try;

import java.util.List;
import java.util.function.Supplier;

public final class Tries {

    private Tries() {
        // ignore
    }

    public static <T> List<Try<T>> repeat(int n, Supplier<T> action) {
        return Stream.<Supplier<T>>continually(action)
                .map(Try::ofSupplier)
                .take(n)
                .toJavaList();
    }

}
