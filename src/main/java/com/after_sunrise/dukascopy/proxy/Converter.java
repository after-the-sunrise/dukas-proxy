package com.after_sunrise.dukascopy.proxy;

import com.google.gson.Gson;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SimpleMessageConverter;

import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
public class Converter implements MessageConverter {

    private final MessageConverter delegate = new SimpleMessageConverter();

    private final Gson gson;

    public Converter(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "Gson is required.");
    }

    @Override
    public Object fromMessage(Message<?> message, Class<?> targetClass) {

        Object converted = delegate.fromMessage(message, targetClass);

        if (converted != null) {
            return converted;
        }

        String text = new String((byte[]) message.getPayload(), UTF_8);

        return CharSequence.class.isAssignableFrom(targetClass) ? text : gson.fromJson(text, targetClass);

    }

    @Override
    public Message<?> toMessage(Object payload, MessageHeaders headers) {

        byte[] bytes = gson.toJson(payload).getBytes(UTF_8);

        return delegate.toMessage(bytes, headers);

    }

}
