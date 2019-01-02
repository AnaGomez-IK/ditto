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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag;

/**
 * Encapsulates responsibility for instantiating {@link MessageMapper} objects.
 * <p>
 * As the message mapper instantiation is usually triggered by an actor, there are only limited possibilities of
 * logging fine grained errors and at the same time keep all responsibility for mapper instantiation behavior away
 * of the actor.
 * </p>
 * <p>
 * Due to this, the factory can be instantiated with a reference to the actors log adapter and will log problems to
 * the debug and warning level (no info and error). Setting a log adapter does not change factory behaviour!
 * </p>
 */
@Immutable
public final class DefaultMessageMapperFactory implements MessageMapperFactory {

    private final Config mappingConfig;

    /**
     * The actor system used for dynamic class instantiation.
     */
    private final DynamicAccess dynamicAccess;

    /**
     * The factory function that creates instances of {@link MessageMapper}.
     */
    private final MessageMapperInstantiation messageMappers;

    private final DiagnosticLoggingAdapter log;

    /**
     * Constructor
     *
     * @param mappingConfig the static service configuration for mapping related stuff
     * @param dynamicAccess the actor systems dynamic access used for dynamic class instantiation
     * @param messageMappers the factory class scanned for factory functions
     * @param log the log adapter used for debug and warning logs
     */
    private DefaultMessageMapperFactory(final Config mappingConfig, final DynamicAccess dynamicAccess,
            final MessageMapperInstantiation messageMappers, final DiagnosticLoggingAdapter log) {

        this.mappingConfig = checkNotNull(mappingConfig);
        this.dynamicAccess = checkNotNull(dynamicAccess);
        this.messageMappers = checkNotNull(messageMappers);
        this.log = checkNotNull(log);
    }

    /**
     * Creates a new factory and returns the instance
     *
     * @param actorSystem the actor system to use for mapping config + dynamicAccess
     * @param log the log adapter used for debug and warning logs
     * @return the new instance
     */
    public static DefaultMessageMapperFactory of(final ActorSystem actorSystem, final DiagnosticLoggingAdapter log) {

        final Config mappingConfig = actorSystem.settings().config().getConfig("ditto.connectivity.mapping");
        final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
        final MessageMapperInstantiation messageMappers = loadMessageMappersInstantiation(mappingConfig, dynamicAccess);
        return new DefaultMessageMapperFactory(mappingConfig, dynamicAccess, messageMappers, log);
    }

    @Override
    public Optional<MessageMapper> mapperOf(final MappingContext mappingContext) {
        final Optional<MessageMapper> mapper = createMessageMapperInstance(mappingContext);
        final MessageMapperConfiguration options = DefaultMessageMapperConfiguration.of(mappingContext.getOptions());
        return mapper.map(m -> configureInstance(m, options) ? m : null);
    }

    @Override
    public MessageMapperRegistry registryOf(final MappingContext defaultContext,
            @Nullable final MappingContext context) {
        final MessageMapper defaultMapper = mapperOf(defaultContext)
                .map(WrappingMessageMapper::wrap)
                .orElseThrow(() -> new IllegalArgumentException("No default mapper found: " + defaultContext));

        final MessageMapper messageMapper;
        if (context != null) {
            messageMapper = mapperOf(context)
                    .map(WrappingMessageMapper::wrap).orElse(null);
        } else {
            messageMapper = null;
        }
        return DefaultMessageMapperRegistry.of(defaultMapper, messageMapper);
    }

    /**
     * Try to instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if it can be instantiated from the configured factory class.
     */
    Optional<MessageMapper> createMessageMapperInstance(final MappingContext mappingContext) {

        return Optional.ofNullable(messageMappers.apply(mappingContext, dynamicAccess));
    }

    private boolean configureInstance(final MessageMapper mapper, final MessageMapperConfiguration options) {
        try {
            mapper.configure(mappingConfig, options);
            return true;
        } catch (final MessageMapperConfigurationInvalidException e) {
            log.warning("Failed to apply configuration <{}> to mapper instance <{}>: {}", options, mapper,
                    e.getMessage());
            return false;
        }
    }

    private static MessageMapperInstantiation loadMessageMappersInstantiation(final Config mappingConfig,
            final DynamicAccess dynamicAccess) {

        try {

            final String className = mappingConfig.getString("factory");
            final ClassTag<MessageMapperInstantiation> tag =
                    scala.reflect.ClassTag$.MODULE$.apply(MessageMapperInstantiation.class);
            return dynamicAccess.createInstanceFor(className, List$.MODULE$.empty(), tag).get();

        } catch (final Exception e) {
            final String message = e.getClass().getCanonicalName() + ": " + e.getMessage();
            throw MessageMapperConfigurationFailedException.newBuilder(message).build();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMessageMapperFactory that = (DefaultMessageMapperFactory) o;
        return Objects.equals(dynamicAccess, that.dynamicAccess) &&
                Objects.equals(messageMappers, that.messageMappers) &&
                Objects.equals(log, that.log);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dynamicAccess, messageMappers, log);
    }

}
