/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.API_VERSION_REJECTED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.AUTHORIZATION_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventExFW;

public class KafkaEventFormatterTest
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final KafkaEventExFW.Builder kafkaEventExRW = new KafkaEventExFW.Builder();

    @Test
    public void shouldFormatAuthorizationFailed()
    {
        // GIVEN
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .authorizationFailed(e -> e
                .typeId(AUTHORIZATION_FAILED.value())
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        KafkaEventFormatter formatter = new KafkaEventFormatter();

        // WHEN
        String result = formatter.formatEventEx(0, eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("AUTHORIZATION_FAILED"));
    }

    @Test
    public void shouldFormatApiVersionRejected()
    {
        // GIVEN
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .apiVersionRejected(e -> e
                .typeId(API_VERSION_REJECTED.value())
                .apiKey(42)
                .apiVersion(7)
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        KafkaEventFormatter formatter = new KafkaEventFormatter();

        // WHEN
        String result = formatter.formatEventEx(0, eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("API_VERSION_REJECTED 42 7"));
    }
}
