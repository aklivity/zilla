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
package io.aklivity.zilla.runtime.catalog.filesystem.internal;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.filesystem.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.filesystem.internal.types.event.FilesystemEventExFW;
import io.aklivity.zilla.runtime.catalog.filesystem.internal.types.event.FilesystemEventType;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class FilesystemEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final MutableDirectBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final FilesystemEventExFW.Builder schemaRegistryEventExRW = new FilesystemEventExFW.Builder();
    private final int filesystemTypeId;
    private final int fileNotFoundEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public FilesystemEventContext(
        EngineContext context)
    {
        this.filesystemTypeId = context.supplyTypeId(FilesystemCatalog.NAME);
        this.fileNotFoundEventId = context.supplyEventId("catalog.filesystem.file.not.found");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void fileNotFound(
        long catalogId,
        String location)
    {
        FilesystemEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .fileNotFound(e -> e
                .typeId(FilesystemEventType.FILE_NOT_FOUND.value())
                .location(location)
            ).build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(fileNotFoundEventId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(filesystemTypeId, event.buffer(), event.offset(), event.limit());
    }
}
