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

import java.util.ArrayList;
import java.util.List;

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
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;

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

    @Test
    public void shouldTransformAcrossMultipleFragments()
    {
        PassthroughFragmentPipeline pipeline = new PassthroughFragmentPipeline();
        KafkaCacheModel model = new KafkaCacheModel(pipeline, new UnsafeBufferEx(new byte[256]));

        KafkaCacheModel.Result first = model.transform(0L, 0L, 0L, FLAGS_INIT, value("hel"), 0, 3, sink);
        assertEquals(ModelStatus.UNDERFLOW, first.status());
        assertEquals(3, first.consumed());
        assertEquals(3, first.produced());

        KafkaCacheModel.Result second = model.transform(0L, 0L, 0L, FLAGS_FIN, value("lo"), 0, 2, sink);
        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals(2, second.consumed());
        assertEquals(2, second.produced());

        assertOutput("hello");
        assertEquals(1, pipeline.resetCount);
        assertEquals(1, (int) pipeline.flagsSeen.stream().filter(f -> (f & FLAGS_INIT) != 0).count());
    }

    @Test
    public void shouldDrainOverflowWithinOneFragment()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[2]));

        KafkaCacheModel.Result result = model.transform(0L, 0L, 0L, FLAGS_INIT | FLAGS_FIN, value("hello"), 0, 5, sink);

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(5, result.produced());
        assertOutput("hello");
    }

    @Test
    public void shouldRejectThenRecoverOnSameInstance()
    {
        KafkaCacheModel model = KafkaCacheModel.decoder(handler(5), ModelTransform.NONE, new UnsafeBufferEx(new byte[256]));

        KafkaCacheModel.Result rejected = model.transform(0L, 0L, 0L, FLAGS_INIT | FLAGS_FIN, value("nope"), 0, 4, sink);
        assertEquals(ModelStatus.REJECTED, rejected.status());

        outputLength.value = 0;
        KafkaCacheModel.Result recovered = model.transform(0L, 0L, 0L, FLAGS_INIT | FLAGS_FIN, value("world"), 0, 5, sink);

        assertEquals(ModelStatus.COMPLETE, recovered.status());
        assertEquals(5, recovered.produced());
        assertOutput("world");
    }

    @Test
    public void shouldRejectWhenPipelineUnderflowsAtFin()
    {
        AlwaysUnderflowPipeline pipeline = new AlwaysUnderflowPipeline();
        KafkaCacheModel model = new KafkaCacheModel(pipeline, new UnsafeBufferEx(new byte[256]));

        KafkaCacheModel.Result result = model.transform(0L, 0L, 0L, FLAGS_INIT | FLAGS_FIN, value("hello"), 0, 5, sink);

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(1, pipeline.resetCount);
    }

    @Test
    public void shouldRetainAndPrependUnconsumedTailAcrossFragments()
    {
        UnderConsumingPipeline pipeline = new UnderConsumingPipeline();
        KafkaCacheModel model = new KafkaCacheModel(pipeline, new UnsafeBufferEx(new byte[256]));

        KafkaCacheModel.Result first = model.transform(0L, 0L, 0L, FLAGS_INIT, value("abc"), 0, 3, sink);
        assertEquals(ModelStatus.UNDERFLOW, first.status());

        KafkaCacheModel.Result second = model.transform(0L, 0L, 0L, 0x00, value("d"), 0, 1, sink);
        assertEquals(ModelStatus.UNDERFLOW, second.status());

        KafkaCacheModel.Result third = model.transform(0L, 0L, 0L, FLAGS_FIN, value("e"), 0, 1, sink);
        assertEquals(ModelStatus.COMPLETE, third.status());

        assertOutput("abcde");
        assertEquals(1, pipeline.resetCount);
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

    // an identity pipeline that echoes exactly what it is given each call and completes only under FIN --
    // stands in for a real pipeline's fragment-boundary contract without any parsing of its own, so tests
    // can isolate KafkaCacheModel's own INIT-once / reset-once-per-value mechanics
    private static final class PassthroughFragmentPipeline implements ModelPipeline
    {
        private static final int FLAGS_FIN = 0x01;

        private final ModelPipelineResult result = new ModelPipelineResult();
        private final List<Integer> flagsSeen = new ArrayList<>();
        private int resetCount;

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
            flagsSeen.add(flags);
            final int length = srcLimit - srcIndex;
            dst.putBytes(dstIndex, src, srcIndex, length);
            final ModelStatus status = (flags & FLAGS_FIN) != 0 ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;
            return result.set(status, length, length);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public void reset()
        {
            resetCount++;
        }
    }

    // a pipeline that never resolves, even when handed FLAGS_FIN, standing in for a non-compliant
    // pipeline so a test can prove KafkaCacheModel's own defensive REJECTED fallback
    private static final class AlwaysUnderflowPipeline implements ModelPipeline
    {
        private final ModelPipelineResult result = new ModelPipelineResult();
        private int resetCount;

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
            final int length = srcLimit - srcIndex;
            dst.putBytes(dstIndex, src, srcIndex, length);
            return result.set(ModelStatus.UNDERFLOW, length, length);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public void reset()
        {
            resetCount++;
        }
    }

    // a pipeline that only ever echoes a single byte per call, forcing KafkaCacheModel to carry the rest
    // of each fragment forward and prepend it to the next one; resolves only once every real byte of the
    // value has finally arrived under FLAGS_FIN, since there is no more input left to wait for by then
    private static final class UnderConsumingPipeline implements ModelPipeline
    {
        private static final int FLAGS_FIN = 0x01;

        private final ModelPipelineResult result = new ModelPipelineResult();
        private int resetCount;

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
            final boolean fin = (flags & FLAGS_FIN) != 0;
            final int available = srcLimit - srcIndex;
            final int consumed = fin ? available : Math.min(1, available);
            dst.putBytes(dstIndex, src, srcIndex, consumed);
            final ModelStatus status = fin ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;
            return result.set(status, consumed, consumed);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public void reset()
        {
            resetCount++;
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
