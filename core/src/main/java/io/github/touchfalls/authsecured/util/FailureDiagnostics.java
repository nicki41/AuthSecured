package io.github.touchfalls.authsecured.util;

/** Diagnostic cause types only: exception messages can contain credentials or raw addresses. */
public final class FailureDiagnostics {
    private FailureDiagnostics() {}

    public static String causeTypes(Throwable error) {
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 4; depth++, current = current.getCause()) {
            if (depth > 0) result.append(" <- ");
            result.append(current.getClass().getSimpleName());
        }
        return result.toString();
    }
}
