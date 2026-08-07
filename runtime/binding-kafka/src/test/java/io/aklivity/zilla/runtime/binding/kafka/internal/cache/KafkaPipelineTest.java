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
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicTransformsType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelFieldBridge;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelHandler;

public class KafkaPipelineTest
{
    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[256]);
    private final MutableDirectBufferEx message = new UnsafeBufferEx(new byte[256]);
    private final List<String> events = new ArrayList<>();
    private final StringBuilder output = new StringBuilder();

    private final KafkaSink recorder = (control, source, event) ->
    {
        events.add(event == KafkaEvent.FIELD
            ? String.format("FIELD(%s=%s)", source.getPath(), text(source.getValue()))
            : event.name());
        return ModelStatus.OK;
    };

    private final KafkaCacheModel.Output sink = (buffer, index, length) ->
        output.append(buffer.getStringWithoutLengthUtf8(index, length));

    @Test
    public void shouldSelectKeyLaneWhileTraversingKey()
    {
        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType("$.id", emptyList());
        KafkaPipeline pipeline = KafkaPipeline.decoder(handler("$.id", "id0"), null, transforms, scratch);

        int transformed = pipeline.transformKey(0L, 0L, message("key0"), 0, 4, sink, recorder);

        assertEquals(4, transformed);
        assertEquals("key0", output.toString());
        // the key lane is both origin and target here: extractKey promotes a field of a structured key to
        // become the key the cache entry is stored under. The pipeline's opening announcement of the lane
        // it is traversing does not reach the terminal, so the only switch the terminal sees is the one
        // the stage raised to append what it found.
        assertEquals(asList("SWITCH_KEY", "FIELD($.id=id0)", "SWITCH_KEY", "FIELD($.id=id0)"), events);
    }

    @Test
    public void shouldSelectHeadersLaneWhileTraversingValue()
    {
        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType(null,
            singletonList(new KafkaTopicHeaderType("region", "$.region")));
        KafkaPipeline pipeline = KafkaPipeline.decoder(null, handler("$.region", "east"), transforms, scratch);

        int transformed = pipeline.transformValue(0L, 0L, message("payload"), 0, 7, sink, recorder);

        assertEquals(7, transformed);
        assertEquals("payload", output.toString());
        assertEquals(asList("SWITCH_HEADERS", "FIELD(region=east)", "SWITCH_VALUE", "FIELD($.region=east)"), events);
    }

    @Test
    public void shouldAppendHeadersInEncounterOrder()
    {
        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType(null,
            asList(new KafkaTopicHeaderType("status", "$.status"), new KafkaTopicHeaderType("region", "$.region")));
        KafkaPipeline pipeline = KafkaPipeline.decoder(null,
            handler("$.region", "east", "$.status", "ok"), transforms, scratch);

        pipeline.transformValue(0L, 0L, message("payload"), 0, 7, sink, recorder);

        // the headers land in the order the fields are encountered, not the order they are configured in
        assertEquals(asList(
            "SWITCH_HEADERS", "FIELD(region=east)", "SWITCH_VALUE", "FIELD($.region=east)",
            "SWITCH_HEADERS", "FIELD(status=ok)", "SWITCH_VALUE", "FIELD($.status=ok)"), events);
    }

    @Test
    public void shouldNotSelectALaneWithoutTransforms()
    {
        KafkaPipeline pipeline = KafkaPipeline.decoder(null, handler("$.region", "east"), null, scratch);

        int transformed = pipeline.transformValue(0L, 0L, message("payload"), 0, 7, sink, recorder);

        assertEquals(7, transformed);
        assertEquals(emptyList(), events);
    }

    @Test
    public void shouldReportLanesWithModels()
    {
        KafkaPipeline pipeline = KafkaPipeline.decoder(handler("$.id", "id0"), null, null, scratch);

        assertTrue(pipeline.transformsKey());
        assertFalse(pipeline.transformsValue());
    }

    @Test
    public void shouldReportNoneWithoutModels()
    {
        assertSame(KafkaPipeline.NONE, KafkaPipeline.decoder(null, null, null, scratch));
        assertFalse(KafkaPipeline.NONE.transformsKey());
        assertFalse(KafkaPipeline.NONE.transformsValue());
        assertEquals(0, KafkaPipeline.NONE.padding(message("x"), 0, 1));

        int transformed = KafkaPipeline.NONE.transformValue(0L, 0L, message("passthrough"), 0, 11, sink, recorder);

        assertEquals(11, transformed);
        assertEquals("passthrough", output.toString());
        assertEquals(emptyList(), events);

        KafkaPipeline.NONE.reset();
    }

    @Test
    public void shouldReportPaddingFromValueLane()
    {
        KafkaPipeline pipeline = KafkaPipeline.decoder(null,
            new TestModelHandler(new TestModelConfig(5, emptyList(), true)), null, scratch);

        assertEquals(0, pipeline.padding(message("hello"), 0, 5));
    }

    @Test
    public void shouldRejectMessageRejectedByModel()
    {
        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType(null,
            singletonList(new KafkaTopicHeaderType("region", "$.region")));
        KafkaPipeline pipeline = KafkaPipeline.decoder(null, rejectingHandler("$.region"), transforms, scratch);

        int transformed = pipeline.transformValue(0L, 0L, message("payload"), 0, 7, sink, recorder);

        assertEquals(-1, transformed);
    }

    @Test
    public void shouldReuseAcrossMessages()
    {
        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType(null,
            singletonList(new KafkaTopicHeaderType("region", "$.region")));
        KafkaPipeline pipeline = KafkaPipeline.decoder(null, handler("$.region", "east"), transforms, scratch);

        pipeline.transformValue(0L, 0L, message("first"), 0, 5, sink, recorder);
        pipeline.reset();
        events.clear();

        pipeline.transformValue(0L, 0L, message("second"), 0, 6, sink, recorder);

        assertEquals(asList("SWITCH_HEADERS", "FIELD(region=east)", "SWITCH_VALUE", "FIELD($.region=east)"), events);
    }

    private MutableDirectBufferEx message(
        String text)
    {
        message.putBytes(0, text.getBytes(UTF_8));
        return message;
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    // a model that copies the value through and surfaces the given path/value pairs as its fields, standing
    // in for a real model decoding a structured key or value
    private static ModelHandler handler(
        String... pathsAndValues)
    {
        return new ModelHandler()
        {
            @Override
            public ModelPipeline supplyDecoder(
                ModelTransform transform)
            {
                return new FieldsPipeline(transform, pathsAndValues, false);
            }

            @Override
            public ModelPipeline supplyEncoder(
                ModelTransform transform)
            {
                return supplyDecoder(transform);
            }
        };
    }

    // a model that surfaces one field and then rejects the value, as a real model does when a value parses
    // far enough to yield fields but fails validation later
    private static ModelHandler rejectingHandler(
        String path)
    {
        return new ModelHandler()
        {
            @Override
            public ModelPipeline supplyDecoder(
                ModelTransform transform)
            {
                return new FieldsPipeline(transform, new String[] { path, "east" }, true);
            }

            @Override
            public ModelPipeline supplyEncoder(
                ModelTransform transform)
            {
                return supplyDecoder(transform);
            }
        };
    }

    private static final class FieldsPipeline implements ModelPipeline
    {
        private final ModelFieldBridge bridge;
        private final String[] pathsAndValues;
        private final boolean rejecting;
        private final MutableDirectBufferEx field = new UnsafeBufferEx(new byte[64]);
        private final ModelPipelineResult result = new ModelPipelineResult();

        private FieldsPipeline(
            ModelTransform transform,
            String[] pathsAndValues,
            boolean rejecting)
        {
            this.bridge = new ModelFieldBridge(transform);
            this.pathsAndValues = pathsAndValues;
            this.rejecting = rejecting;
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int srcLength = srcLimit - srcIndex;

            bridge.start();
            for (int index = 0; index < pathsAndValues.length; index += 2)
            {
                final byte[] value = pathsAndValues[index + 1].getBytes(UTF_8);
                field.putBytes(0, value);
                bridge.field(pathsAndValues[index], field, 0, value.length);
            }
            bridge.end();

            ModelPipelineResult transformed;
            if (rejecting)
            {
                transformed = result.set(ModelStatus.REJECTED, 0, 0);
            }
            else
            {
                dst.putBytes(dstIndex, src, srcIndex, srcLength);
                transformed = result.set(ModelStatus.COMPLETE, srcLength, srcLength);
            }
            return transformed;
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public void reset()
        {
        }
    }
}
