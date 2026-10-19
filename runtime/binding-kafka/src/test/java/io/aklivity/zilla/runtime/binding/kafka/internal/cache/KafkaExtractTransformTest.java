/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

public class KafkaExtractTransformTest
{
    private final List<String> forwarded = new ArrayList<>();
    private final RecordingEnvelope envelope = new RecordingEnvelope();
    private final Content content = new Content();

    private final ModelController control = new ModelController()
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

    private final ModelSink recorder = new ModelSink()
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            forwarded.add(describe(source, event));
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    };

    @Test
    public void shouldSetEnvelopeOnPathMatch()
    {
        ModelTransform stage = new KafkaExtractTransform("$.region", "region", envelope);

        drive(stage, ModelEvent.START_VALUE, null, null);
        drive(stage, ModelEvent.FIELD, "$.region", "east");

        assertEquals("east", envelope.valueOf("region"));
        assertEquals(List.of("START_VALUE", "FIELD($.region=east)"), forwarded);
    }

    @Test
    public void shouldIgnoreUnmatchedPath()
    {
        ModelTransform stage = new KafkaExtractTransform("$.region", "region", envelope);

        drive(stage, ModelEvent.FIELD, "$.status", "ok");

        assertNull(envelope.valueOf("region"));
        assertEquals(List.of("FIELD($.status=ok)"), forwarded);
    }

    @Test
    public void shouldIgnoreNonFieldEvents()
    {
        ModelTransform stage = new KafkaExtractTransform("$.region", "region", envelope);

        drive(stage, ModelEvent.START_VALUE, null, null);
        drive(stage, ModelEvent.END_VALUE, null, null);

        assertNull(envelope.valueOf("region"));
        assertEquals(List.of("START_VALUE", "END_VALUE"), forwarded);
    }

    @Test
    public void shouldComposeMultipleStagesIndependently()
    {
        ModelTransform stages = new KafkaExtractTransform("$.region", "region", envelope)
            .andThen(new KafkaExtractTransform("$.status", "status", envelope));

        drive(stages, ModelEvent.FIELD, "$.region", "east");
        drive(stages, ModelEvent.FIELD, "$.status", "ok");

        assertEquals("east", envelope.valueOf("region"));
        assertEquals("ok", envelope.valueOf("status"));
        assertEquals(List.of("FIELD($.region=east)", "FIELD($.status=ok)"), forwarded);
    }

    @Test
    public void shouldBeIdentity()
    {
        ModelTransform stage = new KafkaExtractTransform("$.region", "region", envelope)
            .andThen(new KafkaExtractTransform("$.status", "status", envelope));

        assertTrue(stage.identity());
    }

    private void drive(
        ModelTransform transform,
        ModelEvent event,
        String path,
        String value)
    {
        content.wrap(path, value);
        transform.transform(control, content, event, recorder);
    }

    private static String describe(
        ModelSource source,
        ModelEvent event)
    {
        return event == ModelEvent.FIELD
            ? String.format("FIELD(%s=%s)", source.getPath(), text(source.getValue()))
            : event.name();
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    private static final class Content implements ModelSource
    {
        private final UnsafeBufferEx view = new UnsafeBufferEx(new byte[0]);

        private String path;

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return view;
        }

        private void wrap(
            String path,
            String value)
        {
            this.path = path;
            view.wrap(value != null ? value.getBytes(UTF_8) : new byte[0]);
        }
    }

    private static final class RecordingEnvelope implements ModelEnvelope
    {
        private final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public int count(
            String name)
        {
            return values.containsKey(name) ? 1 : 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
            values.put(name, value.getStringWithoutLengthUtf8(0, value.capacity()));
        }

        private String valueOf(
            String name)
        {
            return values.get(name);
        }
    }
}
