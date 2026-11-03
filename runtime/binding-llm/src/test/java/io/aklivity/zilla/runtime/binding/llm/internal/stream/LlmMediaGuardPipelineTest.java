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
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

public class LlmMediaGuardPipelineTest
{
    private static final String IMAGE = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64," +
        "aGVsbG8gd29ybGQ=\"}}";
    private static final String AUDIO = "{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"aGVsbG8gd29ybGQ=\"," +
        "\"format\":\"wav\"}}";

    @Test
    public void shouldRelayImageAndPresentDecodedData()
    {
        assertRelayed(IMAGE, "image_url", "url", true, "hello world");
    }

    @Test
    public void shouldRelayAudioAndPresentDecodedData()
    {
        assertRelayed(AUDIO, "input_audio", "data", false, "hello world");
    }

    @Test
    public void shouldRelayMembersAroundDataVerbatim()
    {
        assertRelayed("{ \"a\" : 1 , \"image_url\" : { \"detail\" : \"low\" , \"url\" : \"data:image/png;base64,aGk=\" , " +
            "\"x\" : [ 1 ] } , \"b\" : 2 }", "image_url", "url", true, "hi");
    }

    @Test
    public void shouldNotPresentUrlReference()
    {
        assertRelayed("{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/image.png\"}}",
            "image_url", "url", true, null);
        assertRelayed("{\"type\":\"image_url\",\"image_url\":{\"file_id\":\"file_011abc\"}}",
            "image_url", "url", true, null);
    }

    @Test
    public void shouldRejectOnVerdictBeforeRelayingEndOfData()
    {
        final byte[] bytes = IMAGE.getBytes(UTF_8);
        final Run run = run(bytes, bytes.length, 256, "image_url", "url", true, "hello world");

        assertThat(run.status, equalTo(ModelStatus.REJECTED));
        assertThat(run.output.length(), not(equalTo(bytes.length)));
    }

    @Test
    public void shouldRejectInvalidBase64()
    {
        final byte[] bytes = "{\"input_audio\":{\"data\":\"aGVs*G8=\"}}".getBytes(UTF_8);

        assertThat(run(bytes, bytes.length, 256, "input_audio", "data", false, null).status, equalTo(ModelStatus.REJECTED));
    }

    private void assertRelayed(
        String json,
        String container,
        String member,
        boolean dataUri,
        String presented)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        for (int dstSize = 16; dstSize <= 256; dstSize *= 4)
        {
            for (int chunk = 1; chunk <= bytes.length; chunk++)
            {
                final Run run = run(bytes, chunk, dstSize, container, member, dataUri, null);

                assertThat("status dst " + dstSize + " chunk " + chunk, run.status, equalTo(ModelStatus.COMPLETE));
                assertThat("output dst " + dstSize + " chunk " + chunk, run.output, equalTo(json));
                assertThat("presented dst " + dstSize + " chunk " + chunk, run.presented.isEmpty() ? null :
                    String.join("", run.presented), equalTo(presented));
                assertThat("flags dst " + dstSize + " chunk " + chunk, run.flags(), equalTo(presented != null ? "INIT,FIN" : ""));
            }
        }
    }

    private Run run(
        byte[] bytes,
        int chunk,
        int dstSize,
        String container,
        String member,
        boolean dataUri,
        String rejectOn)
    {
        final RecordingPipeline chain = new RecordingPipeline(rejectOn);
        final LlmMediaGuardPipeline pipeline = new LlmMediaGuardPipeline(chain, container, member, dataUri);
        final UnsafeBufferEx dst = new UnsafeBufferEx(new byte[dstSize]);
        final StringBuilder output = new StringBuilder();

        ModelStatus status = ModelStatus.UNDERFLOW;
        boolean init = true;
        for (int from = 0; from < bytes.length && status != ModelStatus.REJECTED; from += chunk)
        {
            final int limit = Math.min(from + chunk, bytes.length);
            final UnsafeBufferEx src = new UnsafeBufferEx(bytes, 0, limit);

            int position = from;
            do
            {
                final ModelPipelineResult result = pipeline.accept(0L, 0L, 0L, init, limit == bytes.length, src, position,
                    limit, dst, 0, dstSize);

                output.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
                position += result.consumed();
                init = false;
                status = result.status();
            }
            while (status == ModelStatus.OVERFLOW);
        }

        while (status != ModelStatus.REJECTED && status != ModelStatus.COMPLETE)
        {
            status = pipeline.finish(0L, 0L, 0L, dst, 0, dstSize).status();
        }

        return new Run(status, output.toString(), chain.presented, chain.flags);
    }

    private record Run(
        ModelStatus status,
        String output,
        List<String> presented,
        List<Integer> flagged)
    {
        String flags()
        {
            final List<String> names = new ArrayList<>();

            if (!flagged.isEmpty() && Flags.hasInit(flagged.get(0)))
            {
                names.add("INIT");
            }

            if (!flagged.isEmpty() && Flags.hasFin(flagged.get(flagged.size() - 1)))
            {
                names.add("FIN");
            }

            return String.join(",", names);
        }
    }

    private static final class RecordingPipeline implements ModelPipeline
    {
        private final ModelPipelineResult result = new ModelPipelineResult();
        private final String rejectOn;
        private final List<String> presented = new ArrayList<>();
        private final List<Integer> flags = new ArrayList<>();
        private final StringBuilder seen = new StringBuilder();

        RecordingPipeline(
            String rejectOn)
        {
            this.rejectOn = rejectOn;
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final String text = src.getStringWithoutLengthUtf8(srcIndex, srcLimit - srcIndex);

            presented.add(text);
            this.flags.add(flags);
            seen.append(text);

            final boolean rejected = Flags.hasFin(flags) && seen.toString().equals(rejectOn);
            final ModelStatus status = rejected ? ModelStatus.REJECTED :
                Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;

            return result.set(status, srcLimit - srcIndex, 0);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        @Override
        public void reset()
        {
            seen.setLength(0);
        }
    }
}
