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

public class LlmOpenaiRequestTransformTest
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
    public void shouldRenameEachKnownFieldToCanonical()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiRequestTransform(true);

        feed(decoder, recorder, "$.max_tokens", "256");
        feed(decoder, recorder, "$.top_p", "0.9");
        feed(decoder, recorder, "$.n", "2");
        feed(decoder, recorder, "$.presence_penalty", "0.1");
        feed(decoder, recorder, "$.frequency_penalty", "0.2");
        feed(decoder, recorder, "$.top_logprobs", "3");
        feed(decoder, recorder, "$.tool_choice", "auto");
        feed(decoder, recorder, "$.response_format", "json_object");

        assertThat(recorder.events, equalTo(List.of(
            "$.maxOutputTokens=256",
            "$.topP=0.9",
            "$.choiceCount=2",
            "$.presencePenalty=0.1",
            "$.frequencyPenalty=0.2",
            "$.topLogprobs=3",
            "$.toolChoice=auto",
            "$.responseFormat=json_object")));
    }

    @Test
    public void shouldRenameEachKnownFieldToNative()
    {
        Recorder recorder = new Recorder();
        ModelTransform encoder = new LlmOpenaiRequestTransform(false);

        feed(encoder, recorder, "$.maxOutputTokens", "256");
        feed(encoder, recorder, "$.topP", "0.9");
        feed(encoder, recorder, "$.choiceCount", "2");

        assertThat(recorder.events, equalTo(List.of(
            "$.max_tokens=256",
            "$.top_p=0.9",
            "$.n=2")));
    }

    @Test
    public void shouldForwardUnknownFieldUnchanged()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiRequestTransform(true);

        feed(decoder, recorder, "$.model", "gpt-4o");
        feed(decoder, recorder, "$.stream", "true");

        assertThat(recorder.events, equalTo(List.of("$.model=gpt-4o", "$.stream=true")));
    }

    @Test
    public void shouldNotRenameNestedFieldResemblingTopLevelName()
    {
        Recorder recorder = new Recorder();
        ModelTransform decoder = new LlmOpenaiRequestTransform(true);

        feed(decoder, recorder, "$.tools[0].function.parameters.n", "1");

        assertThat(recorder.events, equalTo(List.of("$.tools[0].function.parameters.n=1")));
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
