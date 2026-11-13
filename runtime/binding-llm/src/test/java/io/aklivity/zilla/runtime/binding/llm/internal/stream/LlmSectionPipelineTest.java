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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmSectionPipelineTest
{
    private static final String CITATIONS =
        "[{\"type\":\"url_citation\",\"end_index\":8,\"start_index\":0,\"title\":\"Sky colour\"," +
        "\"url\":\"https://example.com/sky\"},{\"type\":\"file_citation\",\"file_id\":\"file_1\"," +
        "\"filename\":\"a.pdf\",\"index\":0}]";

    private final StringBuilder output = new StringBuilder();
    private final LlmSectionPipeline pipeline = new LlmSectionPipeline(
        LlmOpenaiResponsesCitationsToCanonical.array(), new UnsafeBufferEx(new byte[4096]));

    @Test
    public void shouldReportWhatItDidNotConsumeToACallerThatHoldsTheBytes()
    {
        final byte[] bytes = CITATIONS.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final String expected = convertWhole(buffer, bytes.length);

        int cutsWithUnconsumed = 0;

        for (int cut = 1; cut < bytes.length; cut++)
        {
            output.setLength(0);
            pipeline.begin(new LlmSectionAttributes());

            assertThat("cut " + cut, pipeline.feed(buffer, 0, cut, false, this::collect, false), is(true));

            final int unconsumed = pipeline.unconsumed();
            final int consumed = cut - unconsumed;
            cutsWithUnconsumed += unconsumed > 0 ? 1 : 0;

            assertThat("cut " + cut, pipeline.feed(buffer, consumed, bytes.length - consumed, true, this::collect, false),
                is(true));
            assertThat("cut " + cut, pipeline.unconsumed(), is(0));
            assertThat("cut " + cut, output.toString(), equalTo(expected));
        }

        assertThat(cutsWithUnconsumed, greaterThan(0));
    }

    @Test
    public void shouldKeepWhatItDidNotConsumeForACallerThatDoesNotHoldTheBytes()
    {
        final byte[] bytes = CITATIONS.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final String expected = convertWhole(buffer, bytes.length);

        for (int cut = 1; cut < bytes.length; cut++)
        {
            output.setLength(0);
            pipeline.begin(new LlmSectionAttributes());

            assertThat("cut " + cut, pipeline.feed(buffer, 0, cut, false, this::collect), is(true));
            assertThat("cut " + cut, pipeline.feed(buffer, cut, bytes.length - cut, true, this::collect), is(true));
            assertThat("cut " + cut, output.toString(), equalTo(expected));
        }
    }

    @Test
    public void shouldForwardWithoutFirstAndLastByteAtEveryCut()
    {
        final byte[] bytes = CITATIONS.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final String whole = convertWhole(buffer, bytes.length);
        final String expected = whole.substring(1, whole.length() - 1);

        for (int cut = 1; cut < bytes.length; cut++)
        {
            output.setLength(0);
            pipeline.begin(new LlmSectionAttributes());

            assertThat("cut " + cut, pipeline.feedEdges(buffer, 0, cut, false, this::collect), is(true));
            assertThat("cut " + cut, pipeline.feedEdges(buffer, cut, bytes.length - cut, true, this::collect), is(true));
            assertThat("cut " + cut, output.toString(), equalTo(expected));
        }
    }

    private String convertWhole(
        DirectBufferEx buffer,
        int length)
    {
        output.setLength(0);
        pipeline.begin(new LlmSectionAttributes());
        assertThat(pipeline.feed(buffer, 0, length, true, this::collect), is(true));

        return output.toString();
    }

    private void collect(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        output.append(buffer.getStringWithoutLengthUtf8(offset, length));
    }
}
