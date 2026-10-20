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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmGuardEnvelopeTest
{
    @Test
    public void shouldHoldRequestDirection()
    {
        final LlmGuardEnvelope envelope = new LlmGuardEnvelope("request");

        assertThat(envelope.count("direction"), equalTo(1));
        assertThat(text(envelope.get("direction", 0)), equalTo("request"));
    }

    @Test
    public void shouldHoldResponseDirection()
    {
        final LlmGuardEnvelope envelope = new LlmGuardEnvelope("response");

        assertThat(envelope.count("direction"), equalTo(1));
        assertThat(text(envelope.get("direction", 0)), equalTo("response"));
    }

    @Test
    public void shouldNotHoldOtherNamesOrIndexes()
    {
        final LlmGuardEnvelope envelope = new LlmGuardEnvelope("response");

        assertThat(envelope.count("other"), equalTo(0));
        assertThat(envelope.get("other", 0), nullValue());
        assertThat(envelope.get("direction", 1), nullValue());
    }

    @Test
    public void shouldDiscardWrittenValues()
    {
        final LlmGuardEnvelope envelope = new LlmGuardEnvelope("request");

        envelope.set("direction", new UnsafeBufferEx("response".getBytes(UTF_8)));
        envelope.set("other", new UnsafeBufferEx("value".getBytes(UTF_8)));

        assertThat(envelope.count("direction"), equalTo(1));
        assertThat(text(envelope.get("direction", 0)), equalTo("request"));
        assertThat(envelope.count("other"), equalTo(0));
    }

    private static String text(
        DirectBufferEx buffer)
    {
        final byte[] bytes = new byte[buffer.capacity()];
        buffer.getBytes(0, bytes);

        return new String(bytes, UTF_8);
    }
}
