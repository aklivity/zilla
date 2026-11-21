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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import static io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiRunInputEncoder.Status.MALFORMED;
import static io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiRunInputEncoder.Status.MISMATCHED;
import static io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiRunInputEncoder.Status.PENDING;
import static io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiRunInputEncoder.Status.VALID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class AguiRunInputEncoderTest
{
    private static final String BODY =
        "{\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"messages\":[{\"id\":\"msg-1\",\"role\":\"user\",\"content\":\"Hi\"}]," +
        "\"tools\":[],\"context\":[],\"forwardedProps\":{}}";

    private final AguiRunInputEncoder encoder = new AguiRunInputEncoder("thread-1", "run-1", null);

    @Test
    public void shouldAcceptRunAgentInput()
    {
        assertThat(encoder.encode(bytes(BODY), 0, BODY.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptRunAgentInputWithParentRunId()
    {
        final AguiRunInputEncoder resumed = new AguiRunInputEncoder("thread-1", "run-2", "run-0");
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-2\",\"parentRunId\":\"run-0\",\"messages\":[]}";

        assertThat(resumed.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(resumed.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptIdentifiersLast()
    {
        final String body = "{\"messages\":[{\"id\":\"msg-1\"}],\"threadId\":\"thread-1\",\"runId\":\"run-1\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptNullParentRunId()
    {
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"parentRunId\":null}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptWhitespace()
    {
        final String body = " { \"threadId\" : \"thread-1\" , \"runId\" : \"run-1\" } ";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptEscapedIdentifiers()
    {
        final AguiRunInputEncoder escaped = new AguiRunInputEncoder("thread\"1", "run-1", null);
        final String body = "{\"threadId\":\"thread\\\"1\",\"runId\":\"run-1\"}";

        assertThat(escaped.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(escaped.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptBodySplitAtEveryByte()
    {
        final byte[] bytes = BODY.getBytes(UTF_8);

        for (int index = 0; index < bytes.length; index++)
        {
            assertThat(encoder.encode(new UnsafeBufferEx(bytes), index, index + 1), equalTo(PENDING));
        }

        assertThat(encoder.finish(), equalTo(VALID));
    }

    @Test
    public void shouldAcceptBodyWithOversizedMember()
    {
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"forwardedProps\":{\"x\":\"" +
            "y".repeat(100000) + "\"}}";
        final byte[] bytes = body.getBytes(UTF_8);

        assertThat(encoder.encode(new UnsafeBufferEx(bytes), 0, 4096), equalTo(PENDING));
        assertThat(encoder.encode(new UnsafeBufferEx(bytes), 4096, bytes.length), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(VALID));
    }

    @Test
    public void shouldRejectThreadIdMismatch()
    {
        final String body = "{\"threadId\":\"thread-2\",\"runId\":\"run-1\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectRunIdMismatch()
    {
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-2\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectParentRunIdMismatch()
    {
        final AguiRunInputEncoder resumed = new AguiRunInputEncoder("thread-1", "run-2", "run-0");
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-2\",\"parentRunId\":\"run-9\"}";

        assertThat(resumed.encode(bytes(body), 0, body.length()), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectUnexpectedParentRunId()
    {
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-1\",\"parentRunId\":\"run-0\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectMissingParentRunId()
    {
        final AguiRunInputEncoder resumed = new AguiRunInputEncoder("thread-1", "run-2", "run-0");
        final String body = "{\"threadId\":\"thread-1\",\"runId\":\"run-2\"}";

        assertThat(resumed.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(resumed.finish(), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectMissingThreadId()
    {
        final String body = "{\"runId\":\"run-1\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectMissingRunId()
    {
        final String body = "{\"threadId\":\"thread-1\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectIdentifierThatIsNotString()
    {
        final String body = "{\"threadId\":1,\"runId\":\"run-1\"}";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectOversizedIdentifier()
    {
        final AguiRunInputEncoder oversized = new AguiRunInputEncoder("t".repeat(1000), "run-1", null);
        final String body = "{\"threadId\":\"" + "t".repeat(1000) + "\",\"runId\":\"run-1\"}";

        assertThat(oversized.encode(bytes(body), 0, body.length()), equalTo(MISMATCHED));
    }

    @Test
    public void shouldRejectBodyThatIsNotObject()
    {
        assertThat(encoder.encode(bytes("[1]"), 0, 3), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectKeyWithoutQuotes()
    {
        assertThat(encoder.encode(bytes("{threadId:1}"), 0, 12), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectMissingColon()
    {
        assertThat(encoder.encode(bytes("{\"threadId\" \"thread-1\"}"), 0, 22), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectMissingSeparator()
    {
        assertThat(encoder.encode(bytes("{\"threadId\":\"thread-1\" \"runId\":\"run-1\"}"), 0, 38), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectMalformedValue()
    {
        assertThat(encoder.encode(bytes("{\"threadId\":}"), 0, 13), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectTrailingContent()
    {
        final String body = BODY + " x";

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectEmptyBody()
    {
        assertThat(encoder.finish(), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectTruncatedBody()
    {
        final String body = BODY.substring(0, 40);

        assertThat(encoder.encode(bytes(body), 0, body.length()), equalTo(PENDING));
        assertThat(encoder.finish(), equalTo(MALFORMED));
    }

    @Test
    public void shouldRejectInputAfterFailure()
    {
        assertThat(encoder.encode(bytes("[1]"), 0, 3), equalTo(MALFORMED));
        assertThat(encoder.encode(bytes(BODY), 0, BODY.length()), equalTo(MALFORMED));
        assertThat(encoder.finish(), equalTo(MALFORMED));
    }

    private static UnsafeBufferEx bytes(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
