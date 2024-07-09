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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import static io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventType.RETRIEVED_ARTIFACT_ID;
import static io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventType.RETRIEVED_ARTIFACT_SUBJECT_VERSION;
import static io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventType.UNRETRIEVABLE_ARTIFACT_ID;
import static io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventType.UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION;
import static io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventType.UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION_STALE_ARTIFACT;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.regex.Pattern;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class ApicurioEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final MutableDirectBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final ApicurioEventExFW.Builder apicurioEventExRW = new ApicurioEventExFW.Builder();
    private final int apicurioTypeId;
    private final int unretrievableArtifactSubjectVersionId;
    private final int staleArtifactID;
    private final int unretrievableArtifactId;
    private final int retrievableArtifactSubjectVersionId;
    private final int retrievedArtifactId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public ApicurioEventContext(
        EngineContext context)
    {
        this.apicurioTypeId = context.supplyTypeId(ApicurioCatalog.TYPE);
        String format = toEventFormat(ApicurioCatalog.TYPE);
        this.unretrievableArtifactSubjectVersionId = context.supplyEventId(
            format.formatted("unretrievable.artifact.subject.version"));
        this.staleArtifactID = context.supplyEventId(
            format.formatted("unretrievable.artifact.subject.version.stale.artifact"));
        this.unretrievableArtifactId = context.supplyEventId(
            format.formatted("unretrievable.artifact.id"));
        this.retrievableArtifactSubjectVersionId = context.supplyEventId(
            format.formatted("retrieved.artifact.subject.version"));
        this.retrievedArtifactId = context.supplyEventId(
            format.formatted("retrieved.artifact.id"));
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void onUnretrievableArtifactSubjectVersion(
        long catalogId,
        String subject,
        String version)
    {
        ApicurioEventExFW extension = apicurioEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unretrievableArtifactSubjectVersion(e -> e
                .typeId(UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION.value())
                .subject(subject)
                .version(version)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(unretrievableArtifactSubjectVersionId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(apicurioTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onUnretrievableArtifactSubjectVersionStaleArtifact(
        long catalogId,
        String subject,
        String version,
        int artifactId)
    {
        ApicurioEventExFW extension = apicurioEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unretrievableArtifactSubjectVersionStaleArtifact(e -> e
                .typeId(UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION_STALE_ARTIFACT.value())
                .subject(subject)
                .version(version)
                .artifactId(artifactId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(staleArtifactID)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(apicurioTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onUnretrievableArtifactId(
        long catalogId,
        int artifactId)
    {
        ApicurioEventExFW extension = apicurioEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unretrievableArtifactId(e -> e
                .typeId(UNRETRIEVABLE_ARTIFACT_ID.value())
                .artifactId(artifactId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(unretrievableArtifactId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(apicurioTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onRetrievableArtifactSubjectVersion(
        long catalogId,
        String subject,
        String version)
    {
        ApicurioEventExFW extension = apicurioEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .retrievedArtifactSubjectVersion(e -> e
                .typeId(RETRIEVED_ARTIFACT_SUBJECT_VERSION.value())
                .subject(subject)
                .version(version)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(retrievableArtifactSubjectVersionId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(apicurioTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onRetrievableArtifactId(
        long catalogId,
        int artifactId)
    {
        ApicurioEventExFW extension = apicurioEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .retrievedArtifactId(e -> e
                .typeId(RETRIEVED_ARTIFACT_ID.value())
                .artifactId(artifactId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(retrievedArtifactId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(apicurioTypeId, event.buffer(), event.offset(), event.limit());
    }

    private static String toEventFormat(
        String type)
    {
        return String.format("%s.%%s",
            String.format("catalog.%s",
                Pattern.compile("\\-([a-z])")
                    .matcher(type)
                    .replaceAll(m -> String.format(".%s", m.group(1)))));
    }
}
