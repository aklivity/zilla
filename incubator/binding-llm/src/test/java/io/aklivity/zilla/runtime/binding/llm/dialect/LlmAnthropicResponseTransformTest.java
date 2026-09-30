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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public class LlmAnthropicResponseTransformTest
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
    public void shouldRenameContentBlockIndexToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.index", "0");

        assertThat(recorder.events, equalTo(List.of("$.blockId=0")));
    }

    @Test
    public void shouldRenameContentBlockIndexToNative()
    {
        Recorder recorder = new Recorder();
        ModelTransform encoder = new LlmAnthropicResponseTransform(false, ModelEnvelope.NONE);

        feed(encoder, recorder, "$.blockId", "0");

        assertThat(recorder.events, equalTo(List.of("$.index=0")));
    }

    @Test
    public void shouldRenameStopReasonAndRemapMaxTokensValueToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.delta.stop_reason", "max_tokens");

        assertThat(recorder.events, equalTo(List.of("$.delta.finishReason=length")));
    }

    @Test
    public void shouldRenameStopReasonAndRemapToolUseValueToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.delta.stop_reason", "tool_use");

        assertThat(recorder.events, equalTo(List.of("$.delta.finishReason=tool_call")));
    }

    @Test
    public void shouldRenameStopReasonAndRemapEndTurnValueToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.delta.stop_reason", "end_turn");

        assertThat(recorder.events, equalTo(List.of("$.delta.finishReason=stop")));
    }

    @Test
    public void shouldRemapStopSequenceValueToCanonicalStopButNotRoundTripToOriginal()
    {
        Recorder decoded = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);
        feed(decoder, decoded, "$.delta.stop_reason", "stop_sequence");

        assertThat(decoded.events, equalTo(List.of("$.delta.finishReason=stop")));

        Recorder encoded = new Recorder();
        ModelTransform encoder = new LlmAnthropicResponseTransform(false, ModelEnvelope.NONE);
        feed(encoder, encoded, "$.delta.finishReason", "stop");

        assertThat(encoded.events, equalTo(List.of("$.delta.stop_reason=end_turn")));
    }

    @Test
    public void shouldRenameFinishReasonAndRemapToolCallValueToNative()
    {
        Recorder recorder = new Recorder();
        ModelTransform encoder = new LlmAnthropicResponseTransform(false, ModelEnvelope.NONE);

        feed(encoder, recorder, "$.delta.finishReason", "tool_call");

        assertThat(recorder.events, equalTo(List.of("$.delta.stop_reason=tool_use")));
    }

    @Test
    public void shouldRenameUsageFieldsNestedUnderMessageOnMessageStart()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.message.usage.input_tokens", "25");
        feed(decoder, recorder, "$.message.usage.output_tokens", "1");

        assertThat(recorder.events, equalTo(List.of(
            "$.message.usage.inputTokens=25",
            "$.message.usage.outputTokens=1")));
    }

    @Test
    public void shouldRenameUsageFieldsAtRootOnMessageDelta()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.usage.output_tokens", "15");

        assertThat(recorder.events, equalTo(List.of("$.usage.outputTokens=15")));
    }

    @Test
    public void shouldRenameUsageFieldsToNative()
    {
        Recorder recorder = new Recorder();
        ModelTransform encoder = new LlmAnthropicResponseTransform(false, ModelEnvelope.NONE);

        feed(encoder, recorder, "$.message.usage.inputTokens", "25");
        feed(encoder, recorder, "$.usage.outputTokens", "15");

        assertThat(recorder.events, equalTo(List.of(
            "$.message.usage.input_tokens=25",
            "$.usage.output_tokens=15")));
    }

    @Test
    public void shouldForwardUnknownFieldsUnchanged()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);

        feed(decoder, recorder, "$.type", "message_start");
        feed(decoder, recorder, "$.message.id", "msg_01");
        feed(decoder, recorder, "$.message.model", "claude-3-opus-20240229");
        feed(decoder, recorder, "$.message.role", "assistant");
        feed(decoder, recorder, "$.content_block.type", "tool_use");
        feed(decoder, recorder, "$.content_block.id", "toolu_01");
        feed(decoder, recorder, "$.content_block.name", "get_weather");
        feed(decoder, recorder, "$.delta.type", "input_json_delta");
        feed(decoder, recorder, "$.delta.partial_json", "{\"city\":");

        assertThat(recorder.events, equalTo(List.of(
            "$.type=message_start",
            "$.message.id=msg_01",
            "$.message.model=claude-3-opus-20240229",
            "$.message.role=assistant",
            "$.content_block.type=tool_use",
            "$.content_block.id=toolu_01",
            "$.content_block.name=get_weather",
            "$.delta.type=input_json_delta",
            "$.delta.partial_json={\"city\":")));
    }

    @Test
    public void shouldRoundTripKnownFieldsThroughCanonicalFormWithNoLoss()
    {
        List<String[]> nativeFields = List.of(
            new String[] { "$.index", "0" },
            new String[] { "$.message.usage.input_tokens", "25" },
            new String[] { "$.usage.output_tokens", "15" },
            new String[] { "$.delta.stop_reason", "max_tokens" },
            new String[] { "$.content_block.type", "text" });

        Recorder canonical = new Recorder();
        ModelTransform decoder = new LlmAnthropicResponseTransform(true, ModelEnvelope.NONE);
        for (String[] field : nativeFields)
        {
            feed(decoder, canonical, field[0], field[1]);
        }

        Recorder roundTripped = new Recorder();
        ModelTransform encoder = new LlmAnthropicResponseTransform(false, ModelEnvelope.NONE);
        for (String event : canonical.events)
        {
            int separator = event.indexOf('=');
            feed(encoder, roundTripped, event.substring(0, separator), event.substring(separator + 1));
        }

        List<String> original = nativeFields.stream()
            .map(field -> field[0] + "=" + field[1])
            .toList();
        assertThat(roundTripped.events, equalTo(original));
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
