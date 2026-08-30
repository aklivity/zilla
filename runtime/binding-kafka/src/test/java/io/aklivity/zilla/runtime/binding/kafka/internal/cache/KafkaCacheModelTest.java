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
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.agrona.collections.MutableInteger;
import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelFieldBridge;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelHandler;

public class KafkaCacheModelTest
{
    private final MutableDirectBufferEx value = new UnsafeBufferEx(new byte[256]);
    private final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[256]);
    private final MutableInteger outputLength = new MutableInteger();

    private final KafkaCacheModel.Output sink = (buffer, index, length) ->
    {
        output.putBytes(outputLength.value, buffer, index, length);
        outputLength.value += length;
    };

    @Test
    public void shouldTransformWholeValue()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        int produced = model.transform(0L, 0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(5, produced);
        assertOutput("hello");
    }

    @Test
    public void shouldRejectInvalidValue()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        int produced = model.transform(0L, 0L, 0L, value("nope"), 0, 4, sink);

        assertEquals(-1, produced);
    }

    @Test
    public void shouldTransformWholeValueToLargerLength()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5, 8), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        int produced = model.transform(0L, 0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(8, produced);
        assertEquals(8, outputLength.value);
        byte[] prefix = new byte[5];
        output.getBytes(0, prefix);
        assertArrayEquals("hello".getBytes(UTF_8), prefix);
    }

    @Test
    public void shouldTransformWholeValueToSmallerLength()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5, 3), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        int produced = model.transform(0L, 0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(3, produced);
        assertOutput("hel");
    }

    @Test
    public void shouldTransformAcrossOverflow()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[2]));

        int produced = model.transform(0L, 0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(5, produced);
        assertOutput("hello");
    }

    @Test
    public void shouldEncodeWholeValue()
    {
        KafkaCacheModel model = KafkaCacheModel.encoder(handler(3), ModelEnvelope.NONE, new UnsafeBufferEx(new byte[256]));

        int produced = model.transform(0L, 0L, 0L, value("abc"), 0, 3, sink);

        assertEquals(3, produced);
        assertOutput("abc");
    }

    @Test
    public void shouldForwardEnvelopeToEncoder()
    {
        CapturingHandler handler = new CapturingHandler();
        ModelEnvelope envelope = new KafkaCacheTrailerEnvelope();

        KafkaCacheModel.encoder(handler, envelope, new UnsafeBufferEx(new byte[256]));

        assertSame(envelope, handler.encoderEnvelope);
    }

    @Test
    public void shouldForwardEnvelopeToReader()
    {
        CapturingHandler handler = new CapturingHandler();
        ModelEnvelope envelope = new KafkaCacheHeadersEnvelope();

        KafkaCacheModel.reader(handler, ModelTransform.NONE, envelope, new UnsafeBufferEx(new byte[256]));

        assertSame(envelope, handler.decoderEnvelope);
        assertSame(ModelCache.READ, handler.decoderCache);
    }

    @Test
    public void shouldForwardEnvelopeToWriter()
    {
        CapturingHandler handler = new CapturingHandler();
        ModelEnvelope envelope = new KafkaCacheHeadersEnvelope();

        KafkaCacheModel.writer(handler, ModelTransform.NONE, envelope, new UnsafeBufferEx(new byte[256]));

        assertSame(envelope, handler.decoderEnvelope);
        assertSame(ModelCache.WRITE, handler.decoderCache);
    }

    @Test
    public void shouldForwardWhenNone()
    {
        int produced = KafkaCacheModel.NONE.transform(0L, 0L, 0L, value("passthrough"), 0, 11, sink);

        assertEquals(11, produced);
        assertOutput("passthrough");
    }

    @Test
    public void shouldReportNoneAsDefaults()
    {
        assertSame(KafkaCacheModel.NONE, KafkaCacheModel.decoder(null, ModelTransform.NONE, new UnsafeBufferEx(new byte[8])));
        assertSame(KafkaCacheModel.NONE,
            KafkaCacheModel.encoder(null, ModelEnvelope.NONE, new UnsafeBufferEx(new byte[8])));
        assertEquals(0, KafkaCacheModel.NONE.padding(value("x"), 0, 1));

        KafkaCacheModel.NONE.reset();
    }

    @Test
    public void shouldRejectValueRejectedByModel()
    {
        KafkaCacheModel model = new KafkaCacheModel(
            rejectingHandler("$.id").supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE),
            new UnsafeBufferEx(new byte[256]));

        int produced = model.transform(0L, 0L, 0L, value("hello"), 0, 5, sink);

        assertEquals(-1, produced);
    }

    @Test
    public void shouldReportZeroPadding()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        assertEquals(0, model.padding(value("hello"), 0, 5));
    }

    @Test
    public void shouldResetReusablePipeline()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        model.transform(0L, 0L, 0L, value("hello"), 0, 5, sink);
        model.reset();
        outputLength.value = 0;

        int produced = model.transform(0L, 0L, 0L, value("world"), 0, 5, sink);

        assertEquals(5, produced);
        assertOutput("world");
    }

    private static TestModelHandler handler(
        int length)
    {
        return new TestModelHandler(new TestModelConfig(length, emptyList(), true));
    }

    private static TestModelHandler handler(
        int length,
        int transformLength)
    {
        return new TestModelHandler(new TestModelConfig(length, emptyList(), true, transformLength));
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
                ModelEnvelope envelope,
                ModelTransform transform,
                ModelCache cache)
            {
                return new RejectingPipeline(transform, path);
            }

            @Override
            public ModelPipeline supplyEncoder(
                ModelEnvelope envelope,
                ModelTransform transform)
            {
                return supplyDecoder(envelope, transform, ModelCache.NONE);
            }
        };
    }

    private MutableDirectBufferEx value(
        String text)
    {
        value.putBytes(0, text.getBytes(UTF_8));
        return value;
    }

    private void assertOutput(
        String expected)
    {
        byte[] actual = new byte[outputLength.value];
        output.getBytes(0, actual);
        assertArrayEquals(expected.getBytes(UTF_8), actual);
    }

    // a handler that records the envelope (and, for supplyDecoder, the cache mode) it was last
    // supplied with, so a caller can assert on exactly what KafkaCacheModel forwarded
    private static final class CapturingHandler implements ModelHandler
    {
        private ModelEnvelope encoderEnvelope;
        private ModelEnvelope decoderEnvelope;
        private ModelCache decoderCache;

        @Override
        public ModelPipeline supplyDecoder(
            ModelEnvelope envelope,
            ModelTransform transform,
            ModelCache cache)
        {
            this.decoderEnvelope = envelope;
            this.decoderCache = cache;
            return null;
        }

        @Override
        public ModelPipeline supplyEncoder(
            ModelEnvelope envelope,
            ModelTransform transform)
        {
            this.encoderEnvelope = envelope;
            return null;
        }
    }

    private static final class RejectingPipeline implements ModelPipeline
    {
        private final ModelFieldBridge bridge;
        private final String path;
        private final ModelPipelineResult result = new ModelPipelineResult();

        private RejectingPipeline(
            ModelTransform transform,
            String path)
        {
            this.bridge = new ModelFieldBridge(transform);
            this.path = path;
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
            bridge.start(authorization);
            bridge.field(path, src, srcIndex, srcLimit - srcIndex);
            return result.set(ModelStatus.REJECTED, 0, 0);
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
