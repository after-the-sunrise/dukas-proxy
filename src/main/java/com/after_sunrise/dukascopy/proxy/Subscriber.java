package com.after_sunrise.dukascopy.proxy;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConnectionStatusMessage;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IInstrumentStatusMessage;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.after_sunrise.dukascopy.proxy.Config.CK_SEPARATOR;
import static com.after_sunrise.dukascopy.proxy.Config.CK_SUBSCRIPTION_INSTRUMENT;
import static com.after_sunrise.dukascopy.proxy.Config.CV_SEPARATOR;
import static com.after_sunrise.dukascopy.proxy.Config.CV_SUBSCRIPTION_INSTRUMENT;
import static com.after_sunrise.dukascopy.proxy.Config.ENDPOINT_SUBSCRIPTION;
import static com.after_sunrise.dukascopy.proxy.Config.ENDPOINT_SUBSCRIPTION_CREATE;
import static com.after_sunrise.dukascopy.proxy.Config.ENDPOINT_SUBSCRIPTION_DELETE;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_ACCOUNT;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_BAR;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_MESSAGE;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_SUBSCRIPTION;
import static com.after_sunrise.dukascopy.proxy.Config.TOPIC_TICK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
@RestController
public class Subscriber implements IStrategy {

    private final Logger LOGGER = LoggerFactory.getLogger(Subscription.class);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Clock clock;

    private final Configuration configuration;

    private final SimpMessageSendingOperations template;

    private final AtomicReference<IContext> reference = new AtomicReference<>();

    @Autowired
    public Subscriber(Clock clock, Configuration configuration, SimpMessageSendingOperations template) {

        this.clock = Objects.requireNonNull(clock, "Clock is required.");

        this.configuration = Objects.requireNonNull(configuration, "Configuration is required.");

        this.template = Objects.requireNonNull(template, "SimpMessageSendingOperations is required.");

    }

    @Override
    public void onStart(IContext context) {

        reference.set(context);

        LOGGER.trace("BGN");

        logger.info("Context started : server time = {}", Instant.ofEpochMilli(context.getTime()));

        Subscription subscription = adjustSubscription(null, persistInstruments(null, null));

        template.convertAndSend(TOPIC_SUBSCRIPTION, subscription);

    }

    @Override
    public void onStop() {

        IContext context = reference.getAndSet(null);

        if (context == null) {
            return;
        }

        LOGGER.trace("END");

        logger.info("Context stopped : server time = {}", Instant.ofEpochMilli(context.getTime()));

    }

    @VisibleForTesting
    <T> void consumeIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    @VisibleForTesting
    Map<String, Object> createMap() {

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("xi", UUID.randomUUID().toString());
        map.put("xe", clock.millis());

        consumeIfPresent(reference.get(), v -> {
            map.put("xt", v.getTime());
            map.put("xs", v.isStopped());
        });

        return map;

    }

    @Override
    public void onMessage(IMessage message) {

        Map<String, Object> map = createMap();

        consumeIfPresent(message, v -> {
            map.put("mt", v.getType());
            map.put("mc", v.getCreationTime());
            map.put("mr", v.getReasons());
            map.put("md", v.getContent());
        });

        if (message instanceof IConnectionStatusMessage) {
            IConnectionStatusMessage m = (IConnectionStatusMessage) message;
            map.put("cc", m.isConnected());
        }

        if (message instanceof IInstrumentStatusMessage) {
            IInstrumentStatusMessage m = (IInstrumentStatusMessage) message;
            map.put("in", m.getInstrument().name());
            map.put("it", m.isTradable());
        }

        LOGGER.trace("MSG|{}", map);

        template.convertAndSend(TOPIC_MESSAGE, map);

    }

    @Override
    public void onAccount(IAccount account) {

        Map<String, Object> map = convertAccount(account);

        LOGGER.trace("ACC|{}", map);

        template.convertAndSend(TOPIC_ACCOUNT, map);

    }

    @GetMapping(path = TOPIC_ACCOUNT)
    @ResponseBody
    public Map<String, Object> getAccount() {

        IContext context = reference.get();

        if (context == null) {
            return null;
        }

        return convertAccount(context.getAccount());

    }

    @VisibleForTesting
    Map<String, Object> convertAccount(IAccount account) {

        Map<String, Object> map = createMap();

        consumeIfPresent(account, v -> {
            map.put("ai", v.getAccountId());
            map.put("as", v.getAccountState());
            map.put("ab", v.getBalance());
            map.put("am", v.getUsedMargin());
            map.put("au", v.getUserName());
        });

        return map;

    }

    @Override
    public void onTick(Instrument instrument, ITick tick) {

        Map<String, Object> map = convertTick(instrument, tick);

        LOGGER.trace("TCK|{}", map);

        template.convertAndSend(TOPIC_TICK, map);

    }

    @GetMapping(path = TOPIC_TICK + "/{instrument}")
    @ResponseBody
    public Map<String, Object> getTick(@PathVariable Instrument instrument) throws JFException {

        if (instrument == null) {
            return null;
        }

        IContext context = reference.get();

        if (context == null) {
            return null;
        }

        return convertTick(instrument, context.getHistory().getLastTick(instrument));

    }

    @VisibleForTesting
    Map<String, Object> convertTick(Instrument instrument, ITick tick) {

        Map<String, Object> map = createMap();

        consumeIfPresent(instrument, v -> {
            map.put("in", v.name());
            map.put("is", v.getTickScale());
        });

        consumeIfPresent(tick, v -> {
            map.put("tt", v.getTime());
            map.put("ap", v.getAsk());
            map.put("av", v.getAskVolume());
            map.put("at", v.getTotalAskVolume());
            map.put("bp", v.getBid());
            map.put("bv", v.getBidVolume());
            map.put("bt", v.getTotalBidVolume());
        });

        return map;

    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {

        Map<String, Object> map = convertBar(instrument, period, askBar, bidBar);

        LOGGER.trace("BAR|{}", map);

        template.convertAndSend(TOPIC_BAR, map);

    }

    @GetMapping(path = TOPIC_BAR + "/{instrument}/{period}")
    @ResponseBody
    public Map<String, Object> getBar(@PathVariable Instrument instrument, @PathVariable Period period) throws JFException {

        if (instrument == null || period == null) {
            return null;
        }

        IContext context = reference.get();

        if (context == null) {
            return null;
        }

        IBar askBar = context.getHistory().getBar(instrument, period, OfferSide.ASK, 0);

        IBar bidBar = context.getHistory().getBar(instrument, period, OfferSide.BID, 0);

        return convertBar(instrument, period, askBar, bidBar);

    }

    @VisibleForTesting
    Map<String, Object> convertBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {

        Map<String, Object> map = createMap();

        consumeIfPresent(instrument, v -> {
            map.put("in", v.name());
            map.put("is", v.getTickScale());
        });

        consumeIfPresent(period, v -> {
            map.put("pn", v.name());
            map.put("pu", v.getUnit());
            map.put("pc", v.getNumOfUnits());
        });

        consumeIfPresent(askBar, v -> {
            map.put("ao", v.getOpen());
            map.put("ah", v.getHigh());
            map.put("al", v.getLow());
            map.put("ac", v.getClose());
            map.put("av", v.getVolume());
        });

        consumeIfPresent(bidBar, v -> {
            map.put("bo", v.getOpen());
            map.put("bh", v.getHigh());
            map.put("bl", v.getLow());
            map.put("bc", v.getClose());
            map.put("bv", v.getVolume());
        });

        return map;

    }

    @GetMapping(path = ENDPOINT_SUBSCRIPTION)
    @ResponseBody
    @MessageMapping(ENDPOINT_SUBSCRIPTION)
    @SendTo(TOPIC_SUBSCRIPTION)
    public Subscription getSubscription() {

        logger.info("Fetching subscription.");

        Set<Instrument> instruments = persistInstruments(null, null);

        return ImmutableSubscription.builder().epoch(clock.instant()).instruments(instruments).build();

    }

    @PatchMapping(path = ENDPOINT_SUBSCRIPTION)
    @ResponseBody
    @MessageMapping(ENDPOINT_SUBSCRIPTION_CREATE)
    @SendTo(TOPIC_SUBSCRIPTION)
    public Subscription addSubscription(@Payload @RequestBody Subscription message) {

        logger.info("Adding subscription : {}", message);

        Subscription subscription = requireNonNullElseGet(message, ImmutableSubscription::of);

        Set<Instrument> instruments = persistInstruments(subscription.getInstruments(), Collection::add);

        return adjustSubscription(subscription.getId(), instruments);

    }

    @DeleteMapping(path = ENDPOINT_SUBSCRIPTION)
    @ResponseBody
    @MessageMapping(ENDPOINT_SUBSCRIPTION_DELETE)
    @SendTo(TOPIC_SUBSCRIPTION)
    public Subscription removeSubscription(@Payload @RequestBody Subscription message) {

        logger.info("Removing subscription : {}", message);

        Subscription subscription = requireNonNullElseGet(message, ImmutableSubscription::of);

        Set<Instrument> instruments = persistInstruments(subscription.getInstruments(), Collection::remove);

        return adjustSubscription(subscription.getId(), instruments);

    }

    @VisibleForTesting
    Set<Instrument> persistInstruments(Set<Instrument> instruments, BiConsumer<Set<Instrument>, Instrument> handler) {

        Set<Instrument> results = new HashSet<>();

        String separator = configuration.getString(CK_SEPARATOR, CV_SEPARATOR);

        //
        // Load
        //
        String asIs = configuration.getString(CK_SUBSCRIPTION_INSTRUMENT, CV_SUBSCRIPTION_INSTRUMENT);

        logger.debug("Loaded instruments : {} = {}", CK_SUBSCRIPTION_INSTRUMENT, asIs);

        for (String name : Objects.requireNonNullElse(StringUtils.split(asIs, separator), EMPTY_STRING_ARRAY)) {

            Instrument instrument = Instrument.valueOf(name);

            consumeIfPresent(instrument, results::add);

        }

        if (CollectionUtils.isNotEmpty(instruments)) {

            //
            // Aggregate (Add or Remove)
            //
            instruments.forEach(i -> consumeIfPresent(i, instrument -> handler.accept(results, instrument)));

            //
            // Persist
            //
            String toBe = StringUtils.defaultString(results.stream()
                    .map(Instrument::name).filter(StringUtils::isNotBlank).sorted().collect(joining(separator)));

            configuration.setProperty(CK_SUBSCRIPTION_INSTRUMENT, toBe);

            logger.debug("Saved instruments : {} = {}", CK_SUBSCRIPTION_INSTRUMENT, toBe);

        }

        return results;

    }

    @VisibleForTesting
    Subscription adjustSubscription(String id, Set<Instrument> instruments) {

        Subscription subscription;

        IContext context = reference.get();

        if (context != null) {

            Set<Instrument> current = context.getSubscribedInstruments();

            Set<Instrument> excessive = Sets.difference(current, instruments);

            if (CollectionUtils.isNotEmpty(excessive)) {

                excessive.forEach(i -> logger.debug("Unsubscribing : {} - {}", id, i));

                context.unsubscribeInstruments(new HashSet<>(excessive));

            }

            Set<Instrument> lacking = Sets.difference(instruments, current);

            if (CollectionUtils.isNotEmpty(lacking)) {

                lacking.forEach(i -> logger.debug("Subscribing : {} - {}", id, i));

                context.setSubscribedInstruments(new HashSet<>(lacking), false);

            }

            subscription = ImmutableSubscription.builder()
                    .id(id).epoch(clock.instant()).success(TRUE).instruments(instruments).build();

            logger.debug("Adjusted subscription : {}", subscription);

        } else {

            subscription = ImmutableSubscription.builder()
                    .id(id).epoch(clock.instant()).success(FALSE).instruments(instruments).build();

            logger.debug("Skipped subscription : {}", subscription);

        }

        return subscription;

    }

}
