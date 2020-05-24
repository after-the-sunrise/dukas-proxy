package com.after_sunrise.dukascopy.proxy;

import com.google.common.annotations.VisibleForTesting;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
@Component
public class Launcher extends SpringBootServletInitializer {

    @VisibleForTesting
    static volatile Class<?> main = null;

    public static void main(String[] args) {

        Class<?> clazz = Objects.requireNonNullElse(main, Application.class);

        SpringApplication.run(clazz, args);

    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {

        Class<?> clazz = Objects.requireNonNullElse(main, Application.class);

        return application.sources(clazz);

    }

}
