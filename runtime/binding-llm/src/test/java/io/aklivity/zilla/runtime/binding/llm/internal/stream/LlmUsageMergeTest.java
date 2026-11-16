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
import static org.hamcrest.Matchers.is;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmUsageMergeTest
{
    private static final byte[] PREFIX = "{\"anthropic:usage\":".getBytes(UTF_8);
    private static final int CAPACITY = 1024;

    private final MutableDirectBufferEx state = new UnsafeBufferEx(new byte[CAPACITY]);

    @Test
    public void shouldComposeSingleObject()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":1}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":1}}"));
    }

    @Test
    public void shouldComposeEmptyObject()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{}}"));
    }

    @Test
    public void shouldAppendMembersFromLaterObject()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":25}}"), is(true));
        assertThat(merge("{\"anthropic:usage\":{\"output_tokens\":15}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":15}}"));
    }

    @Test
    public void shouldOverrideValueKeepingFirstSeenPosition()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":1,\"cache\":7}}"), is(true));
        assertThat(merge("{\"anthropic:usage\":{\"output_tokens\":15}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":15,\"cache\":7}}"));
    }

    @Test
    public void shouldOverrideWithLongerAndShorterValues()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"a\":9,\"b\":\"x\",\"c\":1}}"), is(true));
        assertThat(merge("{\"anthropic:usage\":{\"a\":1234567,\"c\":2}}"), is(true));
        assertThat(merge("{\"anthropic:usage\":{\"a\":5,\"b\":\"a longer string value\"}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"a\":5,\"b\":\"a longer string value\",\"c\":2}}"));
    }

    @Test
    public void shouldComposeRepeatedlyWithoutGrowing()
    {
        LlmUsageMerge.reset(state);

        for (int count = 0; count < 5000; count++)
        {
            assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":" + count + ",\"output_tokens\":" + (count * 7) + "}}"),
                is(true));
        }

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input_tokens\":4999,\"output_tokens\":34993}}"));
    }

    @Test
    public void shouldStripWhitespaceOutsideStrings()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{ \"anthropic:usage\" : {\n  \"input_tokens\" : 25 ,\n  \"note\" : \"a b  c\",\n" +
            "  \"details\" : [ { \"modality\" : \"TEXT\", \"count\" : 25 } ]\n } }"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input_tokens\":25,\"note\":\"a b  c\"," +
            "\"details\":[{\"modality\":\"TEXT\",\"count\":25}]}}"));
    }

    @Test
    public void shouldKeepNestedValuesAndStructuralCharactersInStrings()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"cache\":{\"read\":1,\"write\":{\"hot\":2}}," +
            "\"tag\":\"a,}]\\\"b\\\\\",\"n\":null,\"ok\":true,\"f\":1.50}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"cache\":{\"read\":1,\"write\":{\"hot\":2}}," +
            "\"tag\":\"a,}]\\\"b\\\\\",\"n\":null,\"ok\":true,\"f\":1.50}}"));
    }

    @Test
    public void shouldOverrideNestedValue()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"cache\":{\"read\":1},\"input_tokens\":25}}"), is(true));
        assertThat(merge("{\"anthropic:usage\":{\"cache\":{\"read\":1,\"write\":2}}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"cache\":{\"read\":1,\"write\":2},\"input_tokens\":25}}"));
    }

    @Test
    public void shouldMatchKeysByExactSpelling()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"input\":1,\"input_tokens\":2}}"), is(true));
        assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":3}}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input\":1,\"input_tokens\":3}}"));
    }

    @Test
    public void shouldIgnoreMissingClosingBraceOfWrapper()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":15}"), is(true));

        assertThat(compose(), equalTo("{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":15}}"));
    }

    @Test
    public void shouldRejectWhenMergedObjectDoesNotFit()
    {
        final MutableDirectBufferEx small = new UnsafeBufferEx(new byte[32]);

        LlmUsageMerge.reset(small);

        final byte[] json = "{\"anthropic:usage\":{\"input_tokens\":25,\"output_tokens\":15}}".getBytes(UTF_8);

        assertThat(LlmUsageMerge.merge(small, small.capacity(), new UnsafeBufferEx(json), 0, json.length), is(false));
    }

    @Test
    public void shouldRejectMalformedObject()
    {
        LlmUsageMerge.reset(state);

        assertThat(merge("{\"anthropic:usage\":{\"input_tokens\":}}"), is(false));
        assertThat(merge("{\"anthropic:usage\":[1,2]}"), is(false));
        assertThat(merge("{\"anthropic:usage\":{\"a\":1"), is(false));
    }

    private boolean merge(
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        return LlmUsageMerge.merge(state, CAPACITY, new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private String compose()
    {
        final int length = LlmUsageMerge.compose(state, CAPACITY, PREFIX);

        return state.getStringWithoutLengthUtf8(LlmUsageMerge.composedAt(state), length);
    }
}
