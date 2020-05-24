package com.after_sunrise.dukascopy.proxy;

import com.dukascopy.api.IStrategy;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static com.after_sunrise.dukascopy.proxy.Config.CK_CONNECTION_WAIT;
import static com.after_sunrise.dukascopy.proxy.Config.CK_CREDENTIAL_JNLP;
import static com.after_sunrise.dukascopy.proxy.Config.CK_CREDENTIAL_PASS;
import static com.after_sunrise.dukascopy.proxy.Config.CK_CREDENTIAL_USER;
import static com.after_sunrise.dukascopy.proxy.Config.CK_LIFECYCLE_WAIT;
import static com.after_sunrise.dukascopy.proxy.Config.CV_CONNECTION_WAIT;
import static com.after_sunrise.dukascopy.proxy.Config.CV_CREDENTIAL_JNLP;
import static com.after_sunrise.dukascopy.proxy.Config.CV_CREDENTIAL_PASS;
import static com.after_sunrise.dukascopy.proxy.Config.CV_CREDENTIAL_USER;
import static com.after_sunrise.dukascopy.proxy.Config.CV_LIFECYCLE_WAIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
@SpringBootApplication
public class Application implements ISystemListener, InitializingBean, DisposableBean, ThreadFactory, UncaughtExceptionHandler, Runnable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ImmutableConfiguration configuration;

    private final IClient client;

    private final IStrategy strategy;

    private final ThreadFactory delegate;

    private final ScheduledExecutorService executor;

    @Autowired
    public Application(ImmutableConfiguration configuration, IClient client, IStrategy strategy) {

        this.configuration = Objects.requireNonNull(configuration, "Configuration is required.");

        this.client = Objects.requireNonNull(client, "IClient is required.");

        this.strategy = Objects.requireNonNull(strategy, "IStrategy is required.");

        this.delegate = Executors.defaultThreadFactory();

        this.executor = Executors.newSingleThreadScheduledExecutor(this);

    }

    @Override
    public Thread newThread(Runnable r) {

        Thread thread = delegate.newThread(r);

        thread.setDaemon(true);

        thread.setName(getClass().getSimpleName());

        thread.setUncaughtExceptionHandler(this);

        return thread;

    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {

        logger.error("Uncaught exception : {}", t, e);

    }

    @Override
    public void afterPropertiesSet() {

        logger.info("Initializing application.");

        client.setSystemListener(this);

        executor.execute(this);

    }

    @Override
    public void destroy() throws InterruptedException {

        long millis = configuration.getLong(CK_LIFECYCLE_WAIT, CV_LIFECYCLE_WAIT.toMillis());

        logger.info("Terminating application : await = {} ms", millis);

        executor.shutdownNow();

        executor.awaitTermination(millis, MILLISECONDS);

        client.disconnect();

        logger.info("Terminated application. (graceful = {})", executor.isTerminated());

    }

    @Override
    public synchronized void onConnect() {

        logger.info("IClient connected.");

        if (executor.isShutdown()) {
            return;
        }

        long id = client.startStrategy(strategy);

        logger.info("Started strategy : [{}] {}", id, strategy);

    }

    @Override
    public synchronized void onDisconnect() {

        logger.info("IClient disconnected.");

        client.getStartedStrategies().forEach((id, strategy) -> {

            client.stopStrategy(id);

            logger.info("Stopped strategy : [{}] {}", id, strategy);

        });

        if (executor.isShutdown()) {
            return;
        }

        executor.execute(this); // Attempt reconnect.

    }

    @Override
    public void run() {

        String jnlp = configuration.getString(CK_CREDENTIAL_JNLP, CV_CREDENTIAL_JNLP);
        String user = configuration.getString(CK_CREDENTIAL_USER, CV_CREDENTIAL_USER);
        String pass = configuration.getString(CK_CREDENTIAL_PASS, CV_CREDENTIAL_PASS);

        try {

            String hash = DigestUtils.md5DigestAsHex(new ByteArrayInputStream(pass.getBytes(UTF_8)));

            logger.info("IClient connecting... (url={}, user={}, pass=MD5:{})", jnlp, user, hash);

            client.connect(jnlp, user, pass);

        } catch (Exception e) {

            if (executor.isShutdown()) {
                return;
            }

            long millis = configuration.getLong(CK_CONNECTION_WAIT, CV_CONNECTION_WAIT.toMillis());

            logger.warn("IClient connection failure. Reconnecting in {} ms...", millis, e);

            executor.schedule(this, millis, MILLISECONDS);

        }

    }

    @Override
    public void onStart(long processId) {
        logger.info("IClient process started : {}", processId);
    }

    @Override
    public void onStop(long processId) {
        logger.info("IClient process stopped : {}", processId);
    }

}
