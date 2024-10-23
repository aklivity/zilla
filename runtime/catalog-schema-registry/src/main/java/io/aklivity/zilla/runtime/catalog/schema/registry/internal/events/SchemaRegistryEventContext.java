/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.events;

import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.RETRIEVED_SCHEMA_ID;
import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.RETRIEVED_SCHEMA_SUBJECT_VERSION;
import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.UNRETRIEVABLE_SCHEMA_ID;
import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION;
import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION_STALE_SCHEMA;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.regex.Pattern;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class SchemaRegistryEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final SchemaRegistryEventExFW.Builder schemaRegistryEventExRW = new SchemaRegistryEventExFW.Builder();
    private final int schemaRegistryTypeId;
    private final int unretrievableSchemaSubjectVersionId;
    private final int staleSchemaId;
    private final int unretrievableSchemaId;
    private final int retrievableSchemaSubjectVersionId;
    private final int retrievableSchemaId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public SchemaRegistryEventContext(
        EngineContext context,
        String type)
    {
        this(context, type, toEventFormat(type));
    }

    private SchemaRegistryEventContext(
        EngineContext context,
        String type,
        String format)
    {
        this.schemaRegistryTypeId = context.supplyTypeId(type);
        this.unretrievableSchemaSubjectVersionId = context.supplyEventId(
            format.formatted("unretrievable.schema.subject.version"));
        this.staleSchemaId = context.supplyEventId(
            format.formatted("unretrievable.schema.subject.version.stale.schema"));
        this.unretrievableSchemaId = context.supplyEventId(
            format.formatted("unretrievable.schema.id"));
        this.retrievableSchemaSubjectVersionId = context.supplyEventId(
            format.formatted("retrievable.schema.subject.version"));
        this.retrievableSchemaId = context.supplyEventId(
            format.formatted("retrievable.schema.id"));
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void onUnretrievableSchemaSubjectVersion(
        long catalogId,
        String subject,
        String version)
    {
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unretrievableSchemaSubjectVersion(e -> e
                .typeId(UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION.value())
                .subject(subject)
                .version(version)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(unretrievableSchemaSubjectVersionId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onUnretrievableSchemaSubjectVersionStaleSchema(
        long catalogId,
        String subject,
        String version,
        int schemaId)
    {
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unretrievableSchemaSubjectVersionStaleSchema(e -> e
                .typeId(UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION_STALE_SCHEMA.value())
                .subject(subject)
                .version(version)
                .schemaId(schemaId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(staleSchemaId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onUnretrievableSchemaId(
        long catalogId,
        int schemaId)
    {
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unretrievableSchemaId(e -> e
                .typeId(UNRETRIEVABLE_SCHEMA_ID.value())
                .schemaId(schemaId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(unretrievableSchemaId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onRetrievableSchemaSubjectVersion(
        long catalogId,
        String subject,
        String version)
    {
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .retrievableSchemaSubjectVersion(e -> e
                .typeId(RETRIEVED_SCHEMA_SUBJECT_VERSION.value())
                .subject(subject)
                .version(version)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(retrievableSchemaSubjectVersionId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onRetrievableSchemaId(
        long catalogId,
        int schemaId)
    {
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .retrievableSchemaId(e -> e
                .typeId(RETRIEVED_SCHEMA_ID.value())
                .schemaId(schemaId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(retrievableSchemaId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }

    private static String toEventFormat(
        String name)
    {
        return String.format("%s.%%s",
            String.format("catalog.%s",
                Pattern.compile("\\-([a-z])")
                    .matcher(name)
                    .replaceAll(m -> String.format(".%s", m.group(1)))));
    }
}
