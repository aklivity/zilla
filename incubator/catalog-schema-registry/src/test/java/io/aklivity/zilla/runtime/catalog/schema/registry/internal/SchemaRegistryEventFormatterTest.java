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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.REMOTE_ACCESS_REJECTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventExFW;

public class SchemaRegistryEventFormatterTest
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final SchemaRegistryEventExFW.Builder schemaRegistryEventExRW = new SchemaRegistryEventExFW.Builder();

    @Test
    public void shouldFormatRemoteAccessRejected()
    {
        // GIVEN
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .remoteAccessRejected(e -> e
                .typeId(REMOTE_ACCESS_REJECTED.value())
                .method("GET")
                .url("http://localhost:8080/hello")
                .status((short) 500)
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        SchemaRegistryEventFormatter formatter = new SchemaRegistryEventFormatter();

        // WHEN
        String result = formatter.formatEventEx(0, eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("REMOTE_ACCESS_REJECTED GET http://localhost:8080/hello 500"));
    }
}
