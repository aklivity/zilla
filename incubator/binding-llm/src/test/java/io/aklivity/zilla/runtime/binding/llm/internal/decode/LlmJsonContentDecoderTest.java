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
package io.aklivity.zilla.runtime.binding.llm.internal.decode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class LlmJsonContentDecoderTest
{
    private final LlmJsonContentDecoder decoder = new LlmJsonContentDecoder();
    private final StringBuilder data = new StringBuilder();
    private final List<Flushed> flushes = new ArrayList<>();

    private final LlmContentDecoderOutput output = new LlmContentDecoderOutput()
    {
        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            data.append(buffer.getStringWithoutLengthUtf8(offset, length));
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            flushes.add(new Flushed(event, buffer.getStringWithoutLengthUtf8(offset, length), data.toString()));
            data.setLength(0);
        }
    };

    @Before
    public void reset()
    {
        data.setLength(0);
        flushes.clear();
    }

    @Test
    public void shouldForwardNonEmptyRangeAsDataWithoutFlushing()
    {
        int progress = decode("{\"id\":\"1\",\"choices\":[{\"message\":{\"content\":\"hi\"}}]}");

        assertThat(progress, equalTo(byteLength("{\"id\":\"1\",\"choices\":[{\"message\":{\"content\":\"hi\"}}]}")));
        assertThat(flushes.size(), equalTo(0));
        assertThat(data.toString(), equalTo("{\"id\":\"1\",\"choices\":[{\"message\":{\"content\":\"hi\"}}]}"));
    }

    @Test
    public void shouldFullyConsumeBufferInOneCall()
    {
        int progress = decode("{}");

        assertThat(progress, equalTo(byteLength("{}")));
    }

    @Test
    public void shouldReportTerminalFlushOnlyForEmptyRange()
    {
        decode("{}");
        int progress = decode("");

        assertThat(progress, equalTo(0));
        assertThat(flushes.size(), equalTo(1));
        assertThat(flushes.get(0).event, nullValue());
        assertThat(flushes.get(0).id, equalTo(""));
        assertThat(flushes.get(0).data, equalTo("{}"));
    }

    @Test
    public void shouldForwardDataAcrossFragmentsWithOnlyOneTerminalFlush()
    {
        decode("{\"a\":1,");
        decode("\"b\":2}");
        decode("");

        assertThat(flushes.size(), equalTo(1));
        assertThat(flushes.get(0).data, equalTo("{\"a\":1,\"b\":2}"));
    }

    private int decode(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        return decoder.decode(buffer, 0, bytes.length, output);
    }

    private static int byteLength(
        String text)
    {
        return text.getBytes(UTF_8).length;
    }

    private static final class Flushed
    {
        private final String event;
        private final String id;
        private final String data;

        private Flushed(
            String event,
            String id,
            String data)
        {
            this.event = event;
            this.id = id;
            this.data = data;
        }
    }
}
