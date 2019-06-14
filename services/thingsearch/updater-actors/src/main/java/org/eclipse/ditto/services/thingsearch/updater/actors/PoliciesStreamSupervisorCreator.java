/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.streaming.DefaultStreamSupervisor;
import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

/**
 * Creates an actor which is responsible for triggering a cyclic synchronization of all policies which changed within a
 * specified time period.
 */
final class PoliciesStreamSupervisorCreator {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "policiesStreamSupervisor";

    private PoliciesStreamSupervisorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates the props for PoliciesStreamSupervisorCreator.
     *
     * @param thingsUpdater the things updater actor
     * @param pubSubMediator the PubSub mediator Actor.
     * @param streamMetadataPersistence the {@link TimestampPersistence} used to read and write stream metadata (is
     * used to remember the end time of the last stream after a re-start).
     * @param materializer the materializer for the Akka actor system.
     * @param syncConfig the configuration settings for stream consumption.
     * @return the props
     */
    public static Props props(final ActorRef thingsUpdater, final ActorRef pubSubMediator,
            final TimestampPersistence streamMetadataPersistence, final Materializer materializer,
            final SyncConfig syncConfig,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence) {

        return DefaultStreamSupervisor.props(thingsUpdater,
                pubSubMediator,
                PolicyTag.class,
                policyTag -> toPolicyReferenceTags(policyTag, searchUpdaterPersistence),
                PoliciesStreamSupervisorCreator::mapStreamTriggerCommand,
                streamMetadataPersistence,
                materializer,
                syncConfig);
    }

    private static DistributedPubSubMediator.Send mapStreamTriggerCommand(
            final SudoStreamModifiedEntities sudoStreamModifiedEntities) {

        return new DistributedPubSubMediator.Send(PoliciesMessagingConstants.POLICIES_STREAM_PROVIDER_ACTOR_PATH,
                sudoStreamModifiedEntities,
                true);
    }

    private static Source<Object, NotUsed> toPolicyReferenceTags(final PolicyTag policyTag,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence) {

        return searchUpdaterPersistence.getOutdatedThingIds(policyTag)
                .map(thingId -> PolicyReferenceTag.of(thingId, policyTag));
    }

}
