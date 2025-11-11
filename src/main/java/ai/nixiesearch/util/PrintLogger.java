package ai.nixiesearch.util;

import org.slf4j.Logger;
import org.slf4j.Marker;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PrintLogger implements Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum LogLevel {
        TRACE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4);

        private final int priority;

        LogLevel(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }

        public static LogLevel fromString(String level) {
            if (level == null) return INFO;
            switch (level.toUpperCase()) {
                case "TRACE": return TRACE;
                case "DEBUG": return DEBUG;
                case "INFO":  return INFO;
                case "WARN":  return WARN;
                case "ERROR": return ERROR;
                default: return INFO;
            }
        }
    }

    private static volatile LogLevel currentLogLevel = LogLevel.INFO;

    public static void setLogLevel(LogLevel level) {
        currentLogLevel = level;
    }

    public static void setLogLevel(String level) {
        currentLogLevel = LogLevel.fromString(level);
    }

    public static LogLevel getLogLevel() {
        return currentLogLevel;
    }

    private final String name;

    public PrintLogger(String name) {
        this.name = name;
    }

    private void log(LogLevel level, String msg, Throwable t) {
        if (level.getPriority() >= currentLogLevel.getPriority()) {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            System.out.println("[" + timestamp + "] [" + level.name() + "] " + name + " - " + msg);
            if (t != null) t.printStackTrace(System.out);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() { return LogLevel.TRACE.getPriority() >= currentLogLevel.getPriority(); }
    @Override
    public boolean isDebugEnabled() { return LogLevel.DEBUG.getPriority() >= currentLogLevel.getPriority(); }
    @Override
    public boolean isInfoEnabled()  { return LogLevel.INFO.getPriority() >= currentLogLevel.getPriority(); }
    @Override
    public boolean isWarnEnabled()  { return LogLevel.WARN.getPriority() >= currentLogLevel.getPriority(); }
    @Override
    public boolean isErrorEnabled() { return LogLevel.ERROR.getPriority() >= currentLogLevel.getPriority(); }

    @Override
    public void info(String msg)  { log(LogLevel.INFO, msg, null); }
    @Override
    public void debug(String msg) { log(LogLevel.DEBUG, msg, null); }
    @Override
    public void error(String msg) { log(LogLevel.ERROR, msg, null); }
    @Override
    public void warn(String msg)  { log(LogLevel.WARN, msg, null); }
    @Override
    public void trace(String msg) { log(LogLevel.TRACE, msg, null); }

    // You can ignore Marker versions for simplicity
    @Override public void info(String msg, Throwable t)  { log(LogLevel.INFO, msg, t); }
    @Override public void debug(String msg, Throwable t) { log(LogLevel.DEBUG, msg, t); }
    @Override public void error(String msg, Throwable t) { log(LogLevel.ERROR, msg, t); }
    @Override public void warn(String msg, Throwable t)  { log(LogLevel.WARN, msg, t); }
    @Override public void trace(String msg, Throwable t) { log(LogLevel.TRACE, msg, t); }

    // All other overloads can be no-op or delegate to the above
    @Override public void info(String format, Object... arguments)  { info(String.format(format, arguments)); }
    @Override public void debug(String format, Object... arguments) { debug(String.format(format, arguments)); }
    @Override public void error(String format, Object... arguments) { error(String.format(format, arguments)); }
    @Override public void warn(String format, Object... arguments)  { warn(String.format(format, arguments)); }
    @Override public void trace(String format, Object... arguments) { trace(String.format(format, arguments)); }

    // Marker versions can just delegate or no-op
    @Override public boolean isTraceEnabled(Marker marker) { return isTraceEnabled(); }
    @Override public boolean isDebugEnabled(Marker marker) { return isDebugEnabled(); }
    @Override public boolean isInfoEnabled(Marker marker)  { return isInfoEnabled(); }
    @Override public boolean isWarnEnabled(Marker marker)  { return isWarnEnabled(); }
    @Override public boolean isErrorEnabled(Marker marker) { return isErrorEnabled(); }

    @Override public void trace(Marker marker, String msg) { trace(msg); }
    @Override public void debug(Marker marker, String msg) { debug(msg); }
    @Override public void info(Marker marker,  String msg) { info(msg); }
    @Override public void warn(Marker marker,  String msg) { warn(msg); }
    @Override public void error(Marker marker, String msg) { error(msg); }

    @Override public void trace(Marker marker, String msg, Throwable t) { trace(msg, t); }
    @Override public void debug(Marker marker, String msg, Throwable t) { debug(msg, t); }
    @Override public void info(Marker marker,  String msg, Throwable t) { info(msg, t); }
    @Override public void warn(Marker marker,  String msg, Throwable t) { warn(msg, t); }
    @Override public void error(Marker marker, String msg, Throwable t) { error(msg, t); }

    @Override public void trace(String format, Object arg) { trace(String.format(format, arg)); }
    @Override public void trace(String format, Object arg1, Object arg2) { trace(String.format(format, arg1, arg2)); }

    @Override public void debug(String format, Object arg) { debug(String.format(format, arg)); }
    @Override public void debug(String format, Object arg1, Object arg2) { debug(String.format(format, arg1, arg2)); }

    @Override public void info(String format, Object arg) { info(String.format(format, arg)); }
    @Override public void info(String format, Object arg1, Object arg2) { info(String.format(format, arg1, arg2)); }

    @Override public void warn(String format, Object arg) { warn(String.format(format, arg)); }
    @Override public void warn(String format, Object arg1, Object arg2) { warn(String.format(format, arg1, arg2)); }

    @Override public void error(String format, Object arg) { error(String.format(format, arg)); }
    @Override public void error(String format, Object arg1, Object arg2) { error(String.format(format, arg1, arg2)); }

    @Override public void trace(Marker marker, String format, Object arg) { trace(format, arg); }
    @Override public void trace(Marker marker, String format, Object arg1, Object arg2) { trace(format, arg1, arg2); }
    @Override public void trace(Marker marker, String format, Object... arguments) { trace(format, arguments); }

    @Override public void debug(Marker marker, String format, Object arg) { debug(format, arg); }
    @Override public void debug(Marker marker, String format, Object arg1, Object arg2) { debug(format, arg1, arg2); }
    @Override public void debug(Marker marker, String format, Object... arguments) { debug(format, arguments); }

    @Override public void info(Marker marker, String format, Object arg) { info(format, arg); }
    @Override public void info(Marker marker, String format, Object arg1, Object arg2) { info(format, arg1, arg2); }
    @Override public void info(Marker marker, String format, Object... arguments) { info(format, arguments); }

    @Override public void warn(Marker marker, String format, Object arg) { warn(format, arg); }
    @Override public void warn(Marker marker, String format, Object arg1, Object arg2) { warn(format, arg1, arg2); }
    @Override public void warn(Marker marker, String format, Object... arguments) { warn(format, arguments); }

    @Override public void error(Marker marker, String format, Object arg) { error(format, arg); }
    @Override public void error(Marker marker, String format, Object arg1, Object arg2) { error(format, arg1, arg2); }
    @Override public void error(Marker marker, String format, Object... arguments) { error(format, arguments); }
    

}