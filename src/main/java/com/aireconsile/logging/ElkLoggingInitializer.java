package com.aireconsile.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Detaches and stops the LOGSTASH_TCP appender (declared in logback-spring.xml) unless
 * ELK_LOGGING_ENABLED=true, so runs without the ELK stack up (local dev, tests, CI) don't pay the
 * TCP reconnect loop cost or leave a non-daemon thread blocking JVM shutdown. Runs immediately after
 * {@link LoggingApplicationListener} initializes the logging system, before any application logging.
 */
public class ElkLoggingInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final String APPENDER_NAME = "LOGSTASH_TCP";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        boolean elkLoggingEnabled = event.getEnvironment().getProperty("ELK_LOGGING_ENABLED", Boolean.class, false);
        if (elkLoggingEnabled) {
            return;
        }

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<?> appender = rootLogger.getAppender(APPENDER_NAME);
        if (appender != null) {
            rootLogger.detachAppender(APPENDER_NAME);
            appender.stop();
        }
    }

    @Override
    public int getOrder() {
        return LoggingApplicationListener.DEFAULT_ORDER + 1;
    }
}
