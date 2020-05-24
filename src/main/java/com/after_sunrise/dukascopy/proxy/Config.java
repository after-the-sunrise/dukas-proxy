package com.after_sunrise.dukascopy.proxy;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapterFactory;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.DefaultExports;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
@Configuration
public class Config {

    private static final String CONF_PREFIX = "dukas-proxy.";

    public static final String CK_PROPERTIES = CONF_PREFIX + "properties";
    public static final String CV_PROPERTIES = CONF_PREFIX + "properties";

    public static final String CK_LIFECYCLE_WAIT = CONF_PREFIX + "lifecycle.wait";
    public static final Duration CV_LIFECYCLE_WAIT = Duration.ofMinutes(1);

    public static final String CK_SERVER_PORT = CONF_PREFIX + "server.port";
    public static final int CV_SERVER_PORT = 65535;

    public static final String CK_SERVER_METRICS = CONF_PREFIX + "server.metrics";
    public static final String CV_SERVER_METRICS = "/metrics";

    public static final String CK_SERVER_STOMP = CONF_PREFIX + "server.metrics";
    public static final String CV_SERVER_STOMP = "/stomp";

    public static final String CK_CREDENTIAL_JNLP = CONF_PREFIX + "credential.jnlp";
    public static final String CV_CREDENTIAL_JNLP = "http://platform.dukascopy.com/demo/jforex.jnlp";

    public static final String CK_CREDENTIAL_USER = CONF_PREFIX + "credential.user";
    public static final String CV_CREDENTIAL_USER = "foo";

    public static final String CK_CREDENTIAL_PASS = CONF_PREFIX + "credential.pass";
    public static final String CV_CREDENTIAL_PASS = "bar";

    public static final String CK_CONNECTION_WAIT = CONF_PREFIX + "connection.wait";
    public static final Duration CV_CONNECTION_WAIT = Duration.ofSeconds(5);

    public static final String CK_SEPARATOR = CONF_PREFIX + "separator";
    public static final String CV_SEPARATOR = ",";

    public static final String CK_SUBSCRIPTION_INSTRUMENT = CONF_PREFIX + "subscription.instrument";
    public static final String CV_SUBSCRIPTION_INSTRUMENT = "";

    public static final String TOPIC = "/topic";
    public static final String TOPIC_SUBSCRIPTION = TOPIC + "/subscription";
    public static final String TOPIC_MESSAGE = TOPIC + "/message";
    public static final String TOPIC_ACCOUNT = TOPIC + "/account";
    public static final String TOPIC_TICK = TOPIC + "/tick";
    public static final String TOPIC_BAR = TOPIC + "/bar";

    public static final String ENDPOINT_SUBSCRIPTION = "/subscription";
    public static final String ENDPOINT_SUBSCRIPTION_CREATE = ENDPOINT_SUBSCRIPTION + "/create";
    public static final String ENDPOINT_SUBSCRIPTION_DELETE = ENDPOINT_SUBSCRIPTION + "/delete";

    static final Gson GSON;

    static {

        GsonBuilder builder = new GsonBuilder()
                .registerTypeAdapter(
                        Float.class, (JsonSerializer<Float>) (s, t, c) -> new JsonPrimitive(s.toString()))
                .registerTypeAdapter(
                        Double.class, (JsonSerializer<Double>) (s, t, c) -> new JsonPrimitive(s.toString()))
                .registerTypeAdapter(
                        BigDecimal.class, (JsonSerializer<BigDecimal>) (s, t, c) -> new JsonPrimitive(s.toPlainString()))
                .registerTypeAdapter(
                        Instant.class, (JsonSerializer<Instant>) (s, t, c) -> new JsonPrimitive(s.toEpochMilli()))
                .registerTypeAdapter(
                        Instant.class, (JsonDeserializer<Instant>) (j, t, c) -> Instant.ofEpochMilli(j.getAsLong()))
                .registerTypeAdapter(
                        Instrument.class, (JsonSerializer<Instrument>) (s, t, c) -> new JsonPrimitive(s.name()))
                .registerTypeAdapter(
                        Instrument.class, (JsonDeserializer<Instrument>) (j, t, c) -> Instrument.valueOf(j.getAsString()))
                .disableHtmlEscaping();

        ServiceLoader.load(TypeAdapterFactory.class).forEach(builder::registerTypeAdapterFactory);

        GSON = builder.create();

    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final org.apache.commons.configuration2.Configuration configuration;

    public Config() throws IOException {
        configuration = loadConfiguration(System.getProperty(CK_PROPERTIES, CV_PROPERTIES));
    }

    @VisibleForTesting
    org.apache.commons.configuration2.Configuration loadConfiguration(String path) throws IOException {

        CompositeConfiguration c = new CompositeConfiguration();

        c.addConfiguration(new BaseConfiguration());

        c.addConfiguration(new SystemConfiguration());

        Properties p = new Properties();

        try {

            Path absolute = Paths.get(path).toAbsolutePath();

            p.load(new ByteArrayInputStream(Files.readAllBytes(absolute)));

            c.addConfiguration(new MapConfiguration(p));

            logger.info("Loaded filepath properties : {} ({})", absolute, p.size());

        } catch (Exception e1) {

            try {

                URL url = Resources.getResource(path);

                p.load(new ByteArrayInputStream(Resources.toByteArray(url)));

                c.addConfiguration(new MapConfiguration(p));

                logger.info("Loaded classpath properties : {} ({})", url, p.size());

            } catch (Exception e2) {

                logger.debug("Skipped loading properties : {}", path);

            }

        }

        return c;

    }

    @Bean
    public org.apache.commons.configuration2.Configuration configuration() {
        return configuration;
    }

    @Bean
    public Gson gson() {
        return GSON;
    }

    @Bean
    public CollectorRegistry collectorRegistry() {

        CollectorRegistry registry = new CollectorRegistry(true);

        DefaultExports.register(registry);

        return registry;

    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IClient client() throws ReflectiveOperationException {
        return ClientFactory.getDefaultInstance();
    }

    @Bean
    public ServletRegistrationBean<?> servletRegistrationBean(CollectorRegistry registry) {

        String path = configuration.getString(CK_SERVER_METRICS, CV_SERVER_METRICS);

        return new ServletRegistrationBean<>(new MetricsServlet(registry), path);

    }

    @Configuration
    @EnableWebSocketMessageBroker
    public static class WsConfig implements WebSocketMessageBrokerConfigurer, WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

        private final org.apache.commons.configuration2.Configuration configuration;

        private final Gson gson;

        @Autowired
        public WsConfig(org.apache.commons.configuration2.Configuration configuration, Gson gson) {
            this.configuration = Objects.requireNonNull(configuration, "Configuration is required.");
            this.gson = Objects.requireNonNull(gson, "Gson is required.");
        }

        @Override
        public void customize(ConfigurableWebServerFactory factory) {
            factory.setPort(configuration.getInt(CK_SERVER_PORT, CV_SERVER_PORT));
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            config.enableSimpleBroker(TOPIC);
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint(configuration.getString(CK_SERVER_STOMP, CV_SERVER_STOMP)).withSockJS();
        }

        @Override
        public boolean configureMessageConverters(List<MessageConverter> messageConverters) {

            messageConverters.add(new Converter(gson));

            return true; // Add default message converters.

        }

    }

    @Configuration
    @EnableWebMvc
    public static class WebMvcConfig implements WebMvcConfigurer {

        private final Gson gson;

        @Autowired
        public WebMvcConfig(Gson gson) {
            this.gson = Objects.requireNonNull(gson, "Gson is required.");
        }

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(new GsonHttpMessageConverter(gson));
        }

    }

}
