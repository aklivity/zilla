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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmGeminiPartScanner.PLAIN;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmGeminiPartScanner.THOUGHT;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmGeminiPartScanner.UNDECIDED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmGeminiPartScannerTest
{
    @Test
    public void shouldDetectThoughtAfterText()
    {
        assertThat(scanAll("{\"text\":\"Let me think.\",\"thought\":true}"), equalTo(THOUGHT));
    }

    @Test
    public void shouldDetectThoughtBeforeText()
    {
        assertThat(scanAll("{\"thought\": true, \"text\":\"Let me think.\"}"), equalTo(THOUGHT));
    }

    @Test
    public void shouldDetectThoughtAmongOtherMembers()
    {
        assertThat(scanAll("{\"text\":\"a \\\"b\\\"\",\"thoughtSignature\":\"c2ln\",\"x\":{\"thought\":true},\"thought\":true}"),
            equalTo(THOUGHT));
    }

    @Test
    public void shouldNotDetectFalseThought()
    {
        assertThat(scanAll("{\"text\":\"Hello\",\"thought\":false}"), equalTo(PLAIN));
    }

    @Test
    public void shouldNotDetectNestedThought()
    {
        assertThat(scanAll("{\"functionCall\":{\"name\":\"n\",\"args\":{\"thought\":true}}}"), equalTo(PLAIN));
    }

    @Test
    public void shouldNotDetectThoughtInText()
    {
        assertThat(scanAll("{\"text\":\"\\\"thought\\\":true\"}"), equalTo(PLAIN));
    }

    @Test
    public void shouldNotDetectThoughtSignatureAsThought()
    {
        assertThat(scanAll("{\"text\":\"\",\"thoughtSignature\":\"true\"}"), equalTo(PLAIN));
    }

    @Test
    public void shouldStayUndecidedUntilDecisionIsVisible()
    {
        assertUndecidedUntil("{\"text\":\"Let me think.\",\"thought\":true}", THOUGHT);
        assertUndecidedUntil("{\"text\":\"Hello\",\"thoughtSignature\":\"c2ln\"}", PLAIN);
        assertUndecidedUntil("{\"text\":\"Hello\",\"thought\":false,\"n\":[1,{\"a\":\"}\"}]}", PLAIN);
    }

    private static void assertUndecidedUntil(
        String json,
        int expected)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        boolean decided = false;

        for (int limit = 1; limit <= bytes.length; limit++)
        {
            final int status = LlmGeminiPartScanner.scan(buffer, 1, limit);

            if (decided)
            {
                assertThat("limit " + limit, status, equalTo(expected));
            }
            else if (status != UNDECIDED)
            {
                assertThat("limit " + limit, status, equalTo(expected));
                decided = true;
            }
        }

        assertThat(decided, equalTo(true));
    }

    private static int scanAll(
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        return LlmGeminiPartScanner.scan(new UnsafeBufferEx(bytes), 1, bytes.length);
    }
}
