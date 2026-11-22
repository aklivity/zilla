/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.util.Random;

import org.agrona.BitUtil;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class MqttKafkaOffsetHexTest
{
    @Test
    public void shouldEncodeBytesAsLowerCaseHex()
    {
        final MqttKafkaOffsetHex hex = new MqttKafkaOffsetHex();
        final byte[] bytes = {0x01, (byte) 0xab, 0x00, (byte) 0xff, 0x7e};
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        buffer.putBytes(0, bytes);

        assertThat(hex.toHex(buffer, 0, bytes.length).asString(), equalTo("01ab00ff7e"));
    }

    @Test
    public void shouldEncodeFromNonZeroOffset()
    {
        final MqttKafkaOffsetHex hex = new MqttKafkaOffsetHex();
        final byte[] bytes = {0x10, 0x20, 0x30};
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative();
        buffer.putBytes(0, new byte[] {(byte) 0xee, (byte) 0xee});
        buffer.putBytes(2, bytes);

        assertThat(hex.toHex(buffer, 2, bytes.length).asString(), equalTo("102030"));
    }

    @Test
    public void shouldEncodeEmptyRange()
    {
        final MqttKafkaOffsetHex hex = new MqttKafkaOffsetHex();
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8)).asNative();

        assertThat(hex.toHex(buffer, 0, 0).asString(), equalTo(""));
    }

    @Test
    public void shouldMatchAgronaHexForRangeLargerThanInitialCapacity()
    {
        final MqttKafkaOffsetHex hex = new MqttKafkaOffsetHex();
        final byte[] bytes = new byte[5000];
        new Random(7L).nextBytes(bytes);
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(8192)).asNative();
        buffer.putBytes(0, bytes);

        assertThat(hex.toHex(buffer, 0, bytes.length).asString(), equalTo(BitUtil.toHex(bytes)));
    }

    @Test
    public void shouldEncodeShorterRangeAfterLongerOne()
    {
        final MqttKafkaOffsetHex hex = new MqttKafkaOffsetHex();
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocateDirect(1024)).asNative();
        final byte[] longer = new byte[600];
        for (int i = 0; i < longer.length; i++)
        {
            longer[i] = (byte) 0x55;
        }
        buffer.putBytes(0, longer);
        hex.toHex(buffer, 0, longer.length);

        buffer.putBytes(0, new byte[] {0x0a, 0x0b});

        assertThat(hex.toHex(buffer, 0, 2).asString(), equalTo("0a0b"));
    }
}
