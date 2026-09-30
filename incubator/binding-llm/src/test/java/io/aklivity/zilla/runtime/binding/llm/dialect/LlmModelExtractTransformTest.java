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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

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

public class LlmModelExtractTransformTest
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
    public void shouldExtractModelIntoEnvelope()
    {
        Recorder recorder = new Recorder();
        TestModelEnvelope envelope = new TestModelEnvelope();
        ModelTransform transform = new LlmModelExtractTransform(envelope);

        feed(transform, recorder, "$.model", "claude-3-opus-20240229");

        assertThat(recorder.events, equalTo(List.of("$.model=claude-3-opus-20240229")));
        DirectBufferEx extracted = envelope.get("model", 0);
        assertThat(extracted.getStringWithoutLengthUtf8(0, extracted.capacity()), equalTo("claude-3-opus-20240229"));
    }

    @Test
    public void shouldNotExtractNestedFieldNamedModel()
    {
        Recorder recorder = new Recorder();
        TestModelEnvelope envelope = new TestModelEnvelope();
        ModelTransform transform = new LlmModelExtractTransform(envelope);

        feed(transform, recorder, "$.tools[0].function.model", "should-not-be-extracted");

        assertThat(envelope.get("model", 0), nullValue());
    }

    @Test
    public void shouldForwardEveryOtherFieldUnchanged()
    {
        Recorder recorder = new Recorder();
        TestModelEnvelope envelope = new TestModelEnvelope();
        ModelTransform transform = new LlmModelExtractTransform(envelope);

        feed(transform, recorder, "$.max_tokens", "1024");
        feed(transform, recorder, "$.top_p", "0.9");
        feed(transform, recorder, "$.stop_sequences[0]", "\\n");

        assertThat(recorder.events, equalTo(List.of(
            "$.max_tokens=1024",
            "$.top_p=0.9",
            "$.stop_sequences[0]=\\n")));
    }

    @Test
    public void shouldBeIdentity()
    {
        ModelTransform transform = new LlmModelExtractTransform(new TestModelEnvelope());

        assertThat(transform.identity(), is(true));
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
