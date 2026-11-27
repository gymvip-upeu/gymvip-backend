package com.gymvip.config;

import io.sentry.Sentry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackendErrorTelemetry {
    public BackendErrorTelemetry(@Value("${SENTRY_DSN:}") String dsn,
                                 @Value("${SENTRY_ENVIRONMENT:local}") String environment,
                                 @Value("${APP_GIT_COMMIT:${RENDER_GIT_COMMIT:unknown}}") String revision) {
        if (dsn.isBlank()) return;
        Sentry.init(options -> {
            options.setDsn(dsn);
            options.setEnvironment(environment);
            options.setRelease("gymvip-backend@" + revision);
            options.setSendDefaultPii(false);
            options.setEnableUncaughtExceptionHandler(false);
            options.setBeforeSend((event, hint) -> {
                event.setRequest(null);
                event.setUser(null);
                event.setBreadcrumbs(null);
                event.setServerName(null);
                return event;
            });
        });
    }

    public static boolean configured() {
        return Sentry.isEnabled();
    }

    public static void report(Exception exception) {
        if (!configured()) return;
        var safe = new RuntimeException(exception.getClass().getSimpleName());
        safe.setStackTrace(exception.getStackTrace());
        Sentry.captureException(safe);
    }
}
