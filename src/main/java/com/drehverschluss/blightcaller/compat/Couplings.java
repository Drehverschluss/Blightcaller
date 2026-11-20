package com.drehverschluss.blightcaller.compat;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.HashSet;
import java.util.Set;

/**
 * One log line per coupling with another mod (wish 20.11.): exactly one info line when a coupling is set up, one warning
 * with the reason and the fallback when it does not work, nothing else. Every coupling uses this (Jade, Health Bars, later
 * Skill Tree, RunelessRPG, Curios), so the log stays clean and looks the same for all of them.
 * <pre>
 * [Blightcaller] Health Bars found, curse icons attached to its health bars
 * [Blightcaller] Health Bars found, but its config could not be read (NoSuchFieldException: level); using own position above the head
 * </pre>
 */
public final class Couplings {
    private static final Set<String> REPORTED = new HashSet<>();

    private Couplings() {}

    /** The coupling with a mod works: {@code "<mod> found, <what>"}, once per mod. */
    public static synchronized void found(String mod, String what) {
        if (REPORTED.add(mod)) {
            Blightcaller.LOGGER.info("[Blightcaller] {} found, {}", mod, what);
        }
    }

    /**
     * The coupling with a mod does not work: {@code "<mod> found, but <reason>; <fallback>"}, once per mod (also after an
     * earlier {@link #found}, a coupling can break at runtime, e.g. after an update of the other mod).
     */
    public static synchronized void failed(String mod, String reason, String fallback) {
        if (REPORTED.add(mod + "!failed")) {
            Blightcaller.LOGGER.warn("[Blightcaller] {} found, but {}; {}", mod, reason, fallback);
        }
    }

    /** Short reason of an exception for {@link #failed}: its class and message, without the stack trace. */
    public static String reason(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause
                && (cause instanceof java.lang.reflect.InvocationTargetException || cause instanceof ExceptionInInitializerError)) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
