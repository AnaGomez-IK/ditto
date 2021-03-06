/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.signals;

import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

final class ThingModifySignalMapper extends AbstractModifySignalMapper<ThingModifyCommand<?>> {

    @Override
    TopicPathBuilder getTopicPathBuilder(final ThingModifyCommand<?> command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).things();
    }

    @Override
    void enhancePayloadBuilder(final ThingModifyCommand<?> command, final PayloadBuilder payloadBuilder) {
        command.getEntity(command.getImplementedSchemaVersion()).ifPresent(payloadBuilder::withValue);
    }
}
