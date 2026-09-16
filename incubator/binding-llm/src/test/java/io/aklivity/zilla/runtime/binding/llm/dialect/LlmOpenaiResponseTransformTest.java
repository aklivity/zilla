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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public class LlmOpenaiResponseTransformTest
{
    private static final ModelController NO_CONTROL = new ModelController()
    {
        @Override
        public long authorization()
        {
            return 0L;
        }

        @Override
        public void reject(
            String diagnostic)
        {
        }
    };

    @Test
    public void shouldRenameChoiceIndexToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.choices[0].index", "0");
        feed(decoder, recorder, "$.choices[1].index", "1");

        assertThat(recorder.events, equalTo(List.of(
            "$.choices[0].choiceIndex=0",
            "$.choices[1].choiceIndex=1")));
    }

    @Test
    public void shouldRenameFinishReasonAndForwardUnmappedValueUnchanged()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.choices[0].finish_reason", "stop");

        assertThat(recorder.events, equalTo(List.of("$.choices[0].finishReason=stop")));
    }

    @Test
    public void shouldRenameFinishReasonAndRemapToolCallsValueToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.choices[0].finish_reason", "tool_calls");

        assertThat(recorder.events, equalTo(List.of("$.choices[0].finishReason=tool_call")));
    }

    @Test
    public void shouldRenameFinishReasonAndRemapToolCallValueToNative()
    {
        Recorder recorder = new Recorder();
        ModelTransform encoder = new LlmOpenaiResponseTransform(false);

        feed(encoder, recorder, "$.choices[0].finishReason", "tool_call");

        assertThat(recorder.events, equalTo(List.of("$.choices[0].finish_reason=tool_calls")));
    }

    @Test
    public void shouldRenameUsageFields()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.usage.prompt_tokens", "10");
        feed(decoder, recorder, "$.usage.completion_tokens", "5");
        feed(decoder, recorder, "$.usage.total_tokens", "15");

        assertThat(recorder.events, equalTo(List.of(
            "$.usage.inputTokens=10",
            "$.usage.outputTokens=5",
            "$.usage.totalTokens=15")));
    }

    @Test
    public void shouldForwardUnknownRootFieldUnchanged()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.id", "chatcmpl-1");
        feed(decoder, recorder, "$.choices[0].delta.role", "assistant");

        assertThat(recorder.events, equalTo(List.of(
            "$.id=chatcmpl-1",
            "$.choices[0].delta.role=assistant")));
    }

    @Test
    public void shouldNotRenameLogprobsSinceItIsContainerValued()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.choices[0].logprobs.content", "null");

        assertThat(recorder.events, equalTo(List.of("$.choices[0].logprobs.content=null")));
    }

    @Test
    public void shouldNotRenameNestedIndexInsideStreamedToolCallDelta()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiResponseTransform(true);

        feed(decoder, recorder, "$.choices[0].delta.tool_calls[0].index", "0");

        assertThat(recorder.events, equalTo(List.of("$.choices[0].delta.tool_calls[0].index=0")));
    }

    private static void feed(
        ModelTransform transform,
        ModelSink sink,
        String path,
        String value)
    {
        transform.transform(NO_CONTROL, new Field(path, value), ModelEvent.FIELD, sink);
    }

    private static String text(
        ModelSource source)
    {
        DirectBufferEx value = source.getValue();
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    private static final class Field implements ModelSource
    {
        private final String path;
        private final DirectBufferEx value;

        private Field(
            String path,
            String value)
        {
            this.path = path;
            this.value = new UnsafeBufferEx(value.getBytes(UTF_8));
        }

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return value;
        }
    }

    private static final class Recorder implements ModelSink
    {
        private final List<String> events = new ArrayList<>();

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            events.add(source.getPath() + "=" + text(source));
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return false;
        }
    }
}
