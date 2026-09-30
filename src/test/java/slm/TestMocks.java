// SPDX-License-Identifier: GPL-2.0-or-later
package slm;

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/** Explicit non-null boundary for Mockito's unannotated generic factories. */
final class TestMocks {
    private TestMocks() { }

    static <T> @NonNull T verify(T mock) {
        T result = Mockito.verify(mock);
        return Objects.requireNonNull(result);
    }

    static <T> @NonNull T verify(T mock, org.mockito.verification.VerificationMode mode) {
        T result = Mockito.verify(mock, mode);
        return Objects.requireNonNull(result);
    }

    static <T> @NonNull T mock(Class<T> type) {
        T result = Mockito.mock(type);
        return Objects.requireNonNull(result);
    }

    static <T> @NonNull T mock(Class<T> type, Answer<?> answer) {
        T result = Mockito.mock(type, answer);
        return Objects.requireNonNull(result);
    }

    static <T> @NonNull T mock(Class<T> type, MockSettings settings) {
        T result = Mockito.mock(type, settings);
        return Objects.requireNonNull(result);
    }
}
