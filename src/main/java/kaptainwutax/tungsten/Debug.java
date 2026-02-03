package kaptainwutax.tungsten;

import net.minecraft.text.Text;

public class Debug {

    public static TungstenMod jankModInstance;
    private static boolean debugEnabled = true; // Enable debug by default, can be configured

    public static void logInternal(String message) {
        System.out.println("Tungsten: " + message);
    }

    public static void logInternal(String format, Object... args) {
        logInternal(String.format(format, args));
    }

    public static void logInternal(String message, Throwable throwable) {
        System.err.println("Tungsten: " + message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static String getLogPrefix() {
        if (jankModInstance != null) {
            return TungstenMod.getCommandPrefix();
        }
        return "[Tungsten] ";
    }

    public static void logMessage(String message, boolean prefix) {
        if (TungstenMod.mc != null && TungstenMod.mc.player != null) {
            String finalMessage;
            if (prefix) {
                finalMessage = "\u00A72\u00A7l\u00A7o" + getLogPrefix() + "\u00A7r" + message;
            } else {
                finalMessage = message;
            }
            TungstenMod.mc.execute(() -> {
                if (TungstenMod.mc.player != null) {
                    TungstenMod.mc.player.sendMessage(Text.of(finalMessage), false);
                }
            });
        } else {
            logInternal(message);
        }
    }

    public static void logMessage(String message) {
        logMessage(message, true);
    }

    public static void logMessage(String format, Object... args) {
        logMessage(String.format(format, args));
    }

    public static void logWarning(String message) {
        logInternal("WARNING: " + message);
        if (jankModInstance != null) {
            if (TungstenMod.mc != null && TungstenMod.mc.player != null) {
                String msg = "\u00A72\u00A7l\u00A7o" + getLogPrefix() + "\u00A7c" + message + "\u00A7r";
                TungstenMod.mc.execute(() -> {
                    if (TungstenMod.mc.player != null) {
                        TungstenMod.mc.player.sendMessage(Text.of(msg), false);
                    }
                });
            }
        }
    }

    public static void logWarning(String format, Object... args) {
        logWarning(String.format(format, args));
    }

    public static void logError(String message) {
        String stacktrace = getStack(2);
        System.err.println(message);
        System.err.println("at:");
        System.err.println(stacktrace);
        if (TungstenMod.mc != null && TungstenMod.mc.player != null) {
            String msg = "\u00A72\u00A7l\u00A7c" + getLogPrefix() + "[ERROR] " + message + "\nat:\n" + stacktrace + "\u00A7r";
            TungstenMod.mc.execute(() -> {
                if (TungstenMod.mc.player != null) {
                    TungstenMod.mc.player.sendMessage(Text.of(msg), false);
                }
            });
        }
    }

    public static void logError(String format, Object... args) {
        logError(String.format(format, args));
    }

    public static void logStack() {
        logInternal("STACKTRACE: \n" + getStack(2));
    }

    private static String getStack(int toSkip) {
        StringBuilder stacktrace = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (toSkip-- <= 0) {
                stacktrace.append(ste).append("\n");
            }
        }
        return stacktrace.toString();
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }
}

