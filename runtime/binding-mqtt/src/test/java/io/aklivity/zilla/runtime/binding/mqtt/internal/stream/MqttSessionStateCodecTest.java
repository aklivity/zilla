/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.Flyweight;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class MqttSessionStateCodecTest
{
    private static final int VERSION_OFFSET = 0;

    private final MqttSessionStateCodec codec = new MqttSessionStateCodec(1024);

    @Test
    public void shouldEncodeVersion1WithoutSharedSubscriptions()
    {
        final Flyweight state = codec.begin()
            .subscription(1, 1, 1, 0, "sensor/one", null)
            .subscription(2, 0, 0, 0, "sensor/two", null)
            .build();

        assertEquals(1, state.buffer().getByte(state.offset() + VERSION_OFFSET));
    }

    @Test
    public void shouldEncodeVersion1WithoutSubscriptions()
    {
        final Flyweight state = codec.begin().build();

        assertEquals(1, state.buffer().getByte(state.offset() + VERSION_OFFSET));
    }

    @Test
    public void shouldEncodeVersion2WithSharedSubscription()
    {
        final Flyweight state = codec.begin()
            .subscription(1, 1, 1, 0, "sensor/one", null)
            .subscription(2, 2, 0, 0, "sensor/two", "consumers")
            .build();

        assertEquals(2, state.buffer().getByte(state.offset() + VERSION_OFFSET));
    }

    @Test
    public void shouldEncodeVersion1AfterBeginDiscardsSharedSubscription()
    {
        codec.begin().subscription(2, 2, 0, 0, "sensor/two", "consumers");

        final Flyweight state = codec.begin().subscription(1, 1, 1, 0, "sensor/one", null).build();

        assertEquals(1, state.buffer().getByte(state.offset() + VERSION_OFFSET));
    }

    @Test
    public void shouldDecodeVersion1()
    {
        final Flyweight state = codec.begin()
            .subscription(1, 1, 1, 1, "sensor/one", null)
            .build();

        final List<String> decoded = new ArrayList<>();
        final boolean valid = codec.decode(state.buffer(), state.offset(), state.limit(),
            (id, qos, flags, reasonCode, pattern, shareName) ->
                decoded.add(String.format("%d:%d:%d:%d:%s:%s", id, qos, flags, reasonCode, pattern, shareName)));

        assertTrue(valid);
        assertEquals(List.of("1:1:1:1:sensor/one:null"), decoded);
    }

    @Test
    public void shouldDecodeVersion2()
    {
        final Flyweight state = codec.begin()
            .subscription(1, 1, 1, 0, "sensor/one", null)
            .subscription(2, 2, 0, 2, "sensor/two", "consumers")
            .build();

        final List<String> decoded = new ArrayList<>();
        final boolean valid = codec.decode(state.buffer(), state.offset(), state.limit(),
            (id, qos, flags, reasonCode, pattern, shareName) ->
                decoded.add(String.format("%d:%d:%d:%d:%s:%s", id, qos, flags, reasonCode, pattern, shareName)));

        assertTrue(valid);
        assertEquals(List.of("1:1:1:0:sensor/one:null", "2:2:0:2:sensor/two:consumers"), decoded);
    }

    @Test
    public void shouldNotDecodeEmpty()
    {
        final DirectBufferEx buffer = new UnsafeBufferEx(new byte[0]);

        final boolean valid = codec.decode(buffer, 0, 0, (id, qos, flags, reasonCode, pattern, shareName) -> assertNull(pattern));

        assertFalse(valid);
    }
}
