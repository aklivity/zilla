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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String8FW;

public class SseKafkaIdHelperTest
{
    private final SseKafkaIdHelper helper = new SseKafkaIdHelper();

    @Test
    public void shouldDecodeBase64() throws Exception
    {
        DirectBuffer base64 = new String8FW("AQQABAIC").value();
        Array32FW<KafkaOffsetFW> expected =
            new Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024)
                .item(o -> o.partitionId(0).partitionOffset(2))
                .item(o -> o.partitionId(1).partitionOffset(1))
                .item(o -> o.partitionId(-1).partitionOffset(KafkaOffsetType.HISTORICAL.value()))
                .build();


        Array32FW<KafkaOffsetFW> decoded = helper.decode(base64);

        assertNotSame(decoded, helper.historical());
        assertEquals(expected, decoded);
    }

    @Test
    public void shouldDefaultBase64WhenNull() throws Exception
    {
        DirectBuffer base64 = new String8FW(null).value();

        Array32FW<KafkaOffsetFW> decoded = helper.decode(base64);

        assertSame(decoded, helper.historical());
    }

    @Test
    public void shouldDefaultBase64WhenIncomplete() throws Exception
    {
        DirectBuffer base64 = new String8FW("AQQA").value();

        Array32FW<KafkaOffsetFW> decoded = helper.decode(base64);

        assertSame(decoded, helper.historical());
    }

    @Test
    public void shouldRejectBase64WithInvalidLength() throws Exception
    {
        DirectBuffer base64 = new String8FW("AQQABAIC===").value();

        Array32FW<KafkaOffsetFW> decoded = helper.decode(base64);

        assertSame(decoded, helper.historical());
    }

    @Test
    public void shouldRejectBase64WithInvalidChar() throws Exception
    {
        DirectBuffer base64 = new String8FW("AQQABAI^").value();

        Array32FW<KafkaOffsetFW> decoded = helper.decode(base64);

        assertSame(decoded, helper.historical());
    }

    @Test
    public void shouldRejectBase64WithInvalidPaddingChar() throws Exception
    {
        DirectBuffer base64 = new String8FW("AQQABAIC==^=").value();

        Array32FW<KafkaOffsetFW> decoded = helper.decode(base64);

        assertSame(decoded, helper.historical());
    }
}
