/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.karapace.internal;

import static io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventType.RETRIEVED_SCHEMA_ID;
import static io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventType.RETRIEVED_SCHEMA_SUBJECT_VERSION;
import static io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventType.UNRETRIEVABLE_SCHEMA_ID;
import static io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventType.UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION;
import static io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventType.UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION_STALE_SCHEMA;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class KarapaceEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final KarapaceEventExFW.Builder karapaceEventExRW = new KarapaceEventExFW.Builder();
    private final int karapaceTypeId;
    private final int unretrievableSchemaSubjectVersionId;
    private final int staleSchemaID;
    private final int unretrievableSchemaId;
    private final int retrievableSchemaSubjectVersionId;
    private final int retrievableSchemaId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public KarapaceEventContext(
        EngineContext context)
    {
        this.karapaceTypeId = context.supplyTypeId(KarapaceCatalog.NAME);
        this.unretrievableSchemaSubjectVersionId = context.supplyEventId("catalog.karapace.unretrievable.schema.subject.version");
        this.staleSchemaID = context.supplyEventId("catalog.karapace.unretrievable.schema.subject.version.stale.schema");
        this.unretrievableSchemaId = context.supplyEventId("catalog.karapace.unretrievable.schema.id");
        this.retrievableSchemaSubjectVersionId = context.supplyEventId("catalog.karapace.retrievable.schema.subject.version");
        this.retrievableSchemaId = context.supplyEventId("catalog.karapace.retrievable.schema.id");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void unretrievableSchemaSubjectVersion(
        long catalogId,
        String subject,
        String version)
    {
        KarapaceEventExFW extension = karapaceEventExRW
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
        eventWriter.accept(karapaceTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void unretrievableSchemaSubjectVersionStaleSchema(
        long catalogId,
        String subject,
        String version,
        int schemaId)
    {
        KarapaceEventExFW extension = karapaceEventExRW
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
            .id(staleSchemaID)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(karapaceTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void unretrievableSchemaId(
        long catalogId,
        int schemaId)
    {
        KarapaceEventExFW extension = karapaceEventExRW
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
        eventWriter.accept(karapaceTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void retrievableSchemaSubjectVersion(
        long catalogId,
        String subject,
        String version)
    {
        KarapaceEventExFW extension = karapaceEventExRW
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
        eventWriter.accept(karapaceTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void retrievableSchemaId(
        long catalogId,
        int schemaId)
    {
        KarapaceEventExFW extension = karapaceEventExRW
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
        eventWriter.accept(karapaceTypeId, event.buffer(), event.offset(), event.limit());
    }
}
