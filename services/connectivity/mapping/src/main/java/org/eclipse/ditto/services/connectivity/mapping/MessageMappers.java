/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.mapping;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperFactory;

import akka.actor.DynamicAccess;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag;
import scala.util.Try;

/**
 * Factory for creating known {@link MessageMapper} instances and helpers useful for {@link MessageMapper}
 * implementations.
 */
@Immutable
public final class MessageMappers implements MessageMapperInstantiation {

    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");

    /**
     * Create a Rhino mapper if the mapping engine is 'javascript', dynamically instantiate the mapper if the mapping
     * engine is a class name on the class-path, or null otherwise.
     *
     * @param mappingContext the mapping context that configures the mapper.
     * @param dynamicAccess dynamic access to load classes in an actor system.
     * @return the created message mapper instance.
     */
    @Nullable
    @Override
    public MessageMapper apply(final MappingContext mappingContext, final DynamicAccess dynamicAccess) {
        final String mapperName = mappingContext.getMappingEngine();
        if ("javascript".equalsIgnoreCase(mapperName)) {
            return createJavaScriptMessageMapper();
        } else {
            return createAnyMessageMapper(mapperName, dynamicAccess);
        }
    }

    /**
     * Determines the charset from the passed {@code contentType}, falls back to UTF-8 if no specific one was present
     * in contentType.
     *
     * @param contentType the Content-Type to determine the charset from.
     * @return the charset.
     */
    public static Charset determineCharset(@Nullable final CharSequence contentType) {
        if (contentType != null) {
            final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
            if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
                return Charset.forName(withCharset[1]);
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Creates a mapper configuration from the given properties.
     *
     * @param properties the properties.
     * @return the configuration.
     */
    public static MessageMapperConfiguration configurationOf(final Map<String, String> properties) {
        return DefaultMessageMapperConfiguration.of(properties);
    }

    /**
     * Creates a new {@link JavaScriptMessageMapperConfiguration.Builder}.
     *
     * @return the builder.
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder() {
        return createJavaScriptMapperConfigurationBuilder(Collections.emptyMap());
    }

    /**
     * Creates a new {@link JavaScriptMessageMapperConfiguration.Builder} with options.
     *
     * @param options configuration properties to initialize the builder with.
     * @return the builder.
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder(
            final Map<String, String> options) {

        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(options);
    }

    /**
     * Factory method for a rhino mapper.
     *
     * @return the mapper.
     */
    public static MessageMapper createJavaScriptMessageMapper() {
        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
    }

    /**
     * Try to create an instance of any message mapper class on the class-path.
     *
     * @param className name of the message mapper class.
     * @return a new instance of the message mapper class if the mapper can be found and instantiated, or null
     * otherwise.
     */
    @Nullable
    private static MessageMapper createAnyMessageMapper(final String className, final DynamicAccess dynamicAccess) {

        final ClassTag<MessageMapper> tag = scala.reflect.ClassTag$.MODULE$.apply(MessageMapper.class);
        final Try<MessageMapper> mapperTry = dynamicAccess.createInstanceFor(className, List$.MODULE$.empty(), tag);

        if (mapperTry.isFailure()) {
            final Throwable error = mapperTry.failed().get();
            if (error instanceof ClassNotFoundException || error instanceof InstantiationException ||
                    error instanceof ClassCastException) {
                return null;
            } else {
                throw new IllegalStateException("There was an unknown error when trying to creating instance for '"
                        + className + "'", error);
            }
        }

        return mapperTry.get();
    }
}
