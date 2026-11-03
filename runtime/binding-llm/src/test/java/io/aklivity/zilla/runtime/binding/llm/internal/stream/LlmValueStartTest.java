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

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmValueStartTest
{
    @Test
    public void shouldStartAtValueWhenSeparatorConsumed()
    {
        assertThat(valueStart("\"value\""), equalTo(0));
        assertThat(valueStart("{\"a\":1}"), equalTo(0));
        assertThat(valueStart("42"), equalTo(0));
    }

    @Test
    public void shouldSkipSeparatorAfterKey()
    {
        assertThat(valueStart(":\"value\""), equalTo(1));
        assertThat(valueStart(" : \t\r\n{\"a\":1}"), equalTo(6));
    }

    @Test
    public void shouldWaitForValueByStoppingAtLimit()
    {
        assertThat(valueStart(""), equalTo(0));
        assertThat(valueStart(":"), equalTo(1));
        assertThat(valueStart(" : "), equalTo(3));
    }

    private static int valueStart(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);

        return LlmValueStart.of(new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
