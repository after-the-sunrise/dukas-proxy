package com.after_sunrise.dukascopy.proxy;

import com.dukascopy.api.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.after_sunrise.dukascopy.proxy.Config.CK_PROPERTIES;
import static com.after_sunrise.dukascopy.proxy.Config.CK_SUBSCRIPTION_INSTRUMENT;
import static com.after_sunrise.dukascopy.proxy.Config.CV_PROPERTIES;
import static com.after_sunrise.dukascopy.proxy.Config.CV_SEPARATOR;
import static com.after_sunrise.dukascopy.proxy.Config.CV_SERVER_PORT;
import static com.after_sunrise.dukascopy.proxy.Config.CV_SERVER_STOMP;
import static com.after_sunrise.dukascopy.proxy.Config.ENDPOINT_SUBSCRIPTION_CREATE;
import static com.after_sunrise.dukascopy.proxy.Config.ENDPOINT_SUBSCRIPTION_DELETE;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_ACCOUNT;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_BAR;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_MESSAGE;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_SUBSCRIPTION;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_TICK;
import static com.dukascopy.api.Instrument.EURJPY;
import static com.dukascopy.api.Instrument.EURUSD;
import static com.dukascopy.api.Instrument.USDJPY;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
class LauncherTest {

    public static void main(String[] args) {

        //
        // Configure environment variables.
        //
        Properties properties = System.getProperties();
        properties.putIfAbsent(CK_PROPERTIES, Paths.get("logs", CV_PROPERTIES).toAbsolutePath().toString());
        properties.putIfAbsent(CK_SUBSCRIPTION_INSTRUMENT, String.join(CV_SEPARATOR, USDJPY.name(), EURUSD.name()));

        //
        // Launch proxy server.
        //
        Launcher.main(args);

        //
        // Create STOMP client.
        //
        WebSocketStompClient stomp = new WebSocketStompClient(
                new SockJsClient(Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()))));
        stomp.setMessageConverter(new Converter(Config.GSON));
        stomp.start();

        Logger logger = LoggerFactory.getLogger(LauncherTest.class);

        while (stomp.isRunning()) {

            try {

                SECONDS.sleep(15);

                //
                // STOMP endpoint to connect to.
                //
                String endpoint = "ws://localhost:" + CV_SERVER_PORT + CV_SERVER_STOMP;

                logger.info("Connecting : {}", endpoint);

                //
                // Subscribe to STOMP topics and log subscribed messages.
                //
                StompSession ss = stomp.connect(endpoint, new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        logger.info("CON: {}", headers);
                        session.subscribe(TOPIC_SUBSCRIPTION, this);
                        session.subscribe(TOPIC_ACCOUNT, this);
                        session.subscribe(TOPIC_MESSAGE, this);
                        session.subscribe(TOPIC_TICK, this);
                        session.subscribe(TOPIC_BAR, this);
                    }

                    @Override
                    public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable e) {
                        logger.error("EXC: {}", c, e);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        logger.error("ERR", exception);
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        logger.debug("FRM: {} - {}", headers, payload);
                    }
                }).get(1, MINUTES);

                Instrument[] candidates = {USDJPY, EURUSD, EURJPY};

                //
                // Randomly add/remove instrument subscriptions periodically.
                //
                while (ss.isConnected()) {

                    SECONDS.sleep(15);

                    Instrument i = candidates[ThreadLocalRandom.current().nextInt(0, candidates.length)];

                    Subscription s = ImmutableSubscription.builder()
                            .id(UUID.randomUUID().toString()).epoch(Instant.now()).addInstruments(i).build();

                    if (ThreadLocalRandom.current().nextBoolean()) {

                        logger.info("Subscription(+) : {}", s);

                        ss.send(ENDPOINT_SUBSCRIPTION_CREATE, s);

                    } else {

                        logger.info("Subscription(-) : {}", s);

                        ss.send(ENDPOINT_SUBSCRIPTION_DELETE, s);

                    }

                }

            } catch (Throwable e) {

                logger.error("Client failure.", e);

            }

        }

    }

}
