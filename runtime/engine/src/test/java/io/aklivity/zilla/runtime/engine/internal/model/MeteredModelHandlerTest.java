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
package io.aklivity.zilla.runtime.engine.internal.model;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricRecorder;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelRejection;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public class MeteredModelHandlerTest
{
    private static final long BINDING_ID = 0x0001_0000_0000_0002L;
    private static final long AUTHORIZATION = 0x42L;

    private final Map<String, Recording> recorders = new TreeMap<>();
    private final EngineContext context = mock(EngineContext.class);
    private final DirectBufferEx src = new UnsafeBufferEx(new byte[16]);
    private final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[16]);

    private long now;

    @Before
    public void init()
    {
        when(context.supplyMetricRecorder(anyLong(), anyString())).thenAnswer(invocation ->
            recorders.computeIfAbsent(invocation.getArgument(1), name -> new Recording()));
    }

    @Test
    public void shouldCountRejectedInvalid()
    {
        Scripted pipeline = new Scripted(ModelStatus.REJECTED, ModelRejection.INVALID);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(1L), recorded("model.rejected.invalid").values);
        assertEquals(List.of(AUTHORIZATION), recorded("model.rejected.invalid").authorizations);
        assertTrue(recorded("model.rejected.withheld").values.isEmpty());
    }

    @Test
    public void shouldCountRejectedWithheld()
    {
        ModelPipeline metered = decoder(new Scripted(ModelStatus.REJECTED, ModelRejection.WITHHELD), ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(1L), recorded("model.rejected.withheld").values);
        assertTrue(recorded("model.rejected.invalid").values.isEmpty());
    }

    @Test
    public void shouldNotCountRejectionWithoutCategory()
    {
        ModelPipeline metered = decoder(new Scripted(ModelStatus.REJECTED, null), ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertTrue(recorded("model.rejected.invalid").values.isEmpty());
        assertTrue(recorded("model.rejected.withheld").values.isEmpty());
    }

    @Test
    public void shouldCountReplacedAndDeclinedFields()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        pipeline.emit(ModelEvent.FIELD, ModelEvent.REPLACED, ModelEvent.REPLACED, ModelEvent.DECLINED);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(1L, 1L), recorded("model.fields.replaced").values);
        assertEquals(List.of(AUTHORIZATION, AUTHORIZATION), recorded("model.fields.replaced").authorizations);
        assertEquals(List.of(1L), recorded("model.fields.declined").values);
    }

    @Test
    public void shouldDelegateEventsToSuppliedTransform()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        pipeline.emit(ModelEvent.FIELD, ModelEvent.REPLACED);
        Passing transform = new Passing();
        ModelPipeline metered = decoder(pipeline, transform);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(ModelEvent.FIELD, ModelEvent.REPLACED), transform.events);
        assertEquals(List.of(1L), recorded("model.fields.replaced").values);
    }

    @Test
    public void shouldRecordDurationOfCompletedValue()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        pipeline.tick = 250L;
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(250L), recorded("model.transform.duration").values);
        assertEquals(List.of(AUTHORIZATION), recorded("model.transform.duration").authorizations);
    }

    @Test
    public void shouldRecordDurationAcrossSuspendedTransform()
    {
        Scripted pipeline = new Scripted(ModelStatus.SUSPENDED, null);
        pipeline.tick = 100L;
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);
        assertTrue(recorded("model.transform.duration").values.isEmpty());

        now += 5_000L;
        pipeline.tick = 0L;
        pipeline.status = ModelStatus.COMPLETE;
        metered.transform(1L, BINDING_ID, AUTHORIZATION, 0, src, 0, 0, dst, 0, 16);

        assertEquals(List.of(5_100L), recorded("model.transform.duration").values);
    }

    @Test
    public void shouldRecordDurationAcrossUnderflow()
    {
        Scripted pipeline = new Scripted(ModelStatus.UNDERFLOW, null);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT, src, 0, 2, dst, 0, 16);
        now += 700L;
        pipeline.status = ModelStatus.COMPLETE;
        metered.transform(1L, BINDING_ID, AUTHORIZATION, FIN, src, 0, 2, dst, 0, 16);

        assertEquals(List.of(700L), recorded("model.transform.duration").values);
    }

    @Test
    public void shouldRecordDurationOfRejectedValue()
    {
        Scripted pipeline = new Scripted(ModelStatus.REJECTED, ModelRejection.INVALID);
        pipeline.tick = 30L;
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(30L), recorded("model.transform.duration").values);
    }

    @Test
    public void shouldRestartDurationAfterReset()
    {
        Scripted pipeline = new Scripted(ModelStatus.UNDERFLOW, null);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT, src, 0, 2, dst, 0, 16);
        now += 900L;
        metered.reset();
        now += 10L;
        pipeline.status = ModelStatus.COMPLETE;
        pipeline.tick = 20L;
        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 2, dst, 0, 16);

        assertEquals(List.of(20L), recorded("model.transform.duration").values);
    }

    @Test
    public void shouldSupplyRecordersOncePerBinding()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        for (int i = 0; i < 3; i++)
        {
            metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);
        }

        verify(context, times(1)).supplyMetricRecorder(eq(BINDING_ID), eq("model.transform.duration"));
        verify(context, times(1)).supplyMetricRecorder(eq(BINDING_ID), eq("model.fields.replaced"));
        verify(context, times(1)).supplyMetricRecorder(eq(BINDING_ID), eq("model.fields.declined"));
        verify(context, times(1)).supplyMetricRecorder(eq(BINDING_ID), eq("model.rejected.invalid"));
        verify(context, times(1)).supplyMetricRecorder(eq(BINDING_ID), eq("model.rejected.withheld"));
        assertEquals(3, recorded("model.transform.duration").values.size());
    }

    @Test
    public void shouldSupplyRecordersPerBinding()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);
        final long otherBindingId = BINDING_ID + 1L;

        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);
        metered.transform(1L, otherBindingId, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);
        metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        verify(context, times(1)).supplyMetricRecorder(eq(BINDING_ID), eq("model.transform.duration"));
        verify(context, times(1)).supplyMetricRecorder(eq(otherBindingId), eq("model.transform.duration"));
    }

    @Test
    public void shouldDelegatePipelineProperties()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        assertEquals(pipeline.identity(), metered.identity());
        assertEquals(pipeline.deterministic(), metered.deterministic());
        assertEquals(7, metered.padding(src, 0, 4));

        metered.reset();
        assertEquals(1, pipeline.resets);
    }

    @Test
    public void shouldSupplyEncoderWithSameMetrics()
    {
        Scripted pipeline = new Scripted(ModelStatus.REJECTED, ModelRejection.WITHHELD);
        ModelHandler metered = new MeteredModelHandler(new Handler(pipeline), context, () -> now);

        metered.supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE)
            .transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertEquals(List.of(1L), recorded("model.rejected.withheld").values);
    }

    @Test
    public void shouldPreserveResultInstance()
    {
        Scripted pipeline = new Scripted(ModelStatus.COMPLETE, null);
        ModelPipeline metered = decoder(pipeline, ModelTransform.NONE);

        ModelPipelineResult result = metered.transform(1L, BINDING_ID, AUTHORIZATION, INIT | FIN, src, 0, 4, dst, 0, 16);

        assertSame(pipeline.result, result);
    }

    private ModelPipeline decoder(
        Scripted pipeline,
        ModelTransform transform)
    {
        ModelHandler metered = new MeteredModelHandler(new Handler(pipeline), context, () -> now);
        return metered.supplyDecoder(ModelEnvelope.NONE, transform, ModelCache.NONE);
    }

    private Recording recorded(
        String metric)
    {
        return recorders.computeIfAbsent(metric, name -> new Recording());
    }

    private static final class Recording implements MetricRecorder
    {
        private final List<Long> authorizations = new ArrayList<>();
        private final List<Long> values = new ArrayList<>();

        @Override
        public void record(
            long authorization,
            long value)
        {
            authorizations.add(authorization);
            values.add(value);
        }
    }

    private final class Handler implements ModelHandler
    {
        private final Scripted pipeline;

        private Handler(
            Scripted pipeline)
        {
            this.pipeline = pipeline;
        }

        @Override
        public ModelPipeline supplyDecoder(
            ModelEnvelope envelope,
            ModelTransform transform,
            ModelCache cache)
        {
            pipeline.transform = transform;
            return pipeline;
        }

        @Override
        public ModelPipeline supplyEncoder(
            ModelEnvelope envelope,
            ModelTransform transform)
        {
            pipeline.transform = transform;
            return pipeline;
        }
    }

    private final class Scripted implements ModelPipeline
    {
        private final ModelPipelineResult result = new ModelPipelineResult();
        private final List<ModelEvent> events = new ArrayList<>();
        private final ModelController control = new ModelController()
        {
            @Override
            public long authorization()
            {
                return authorization;
            }

            @Override
            public void reject(
                String diagnostic)
            {
            }
        };

        private ModelStatus status;
        private ModelRejection rejection;
        private ModelTransform transform;
        private long tick;
        private long authorization;
        private int resets;

        private Scripted(
            ModelStatus status,
            ModelRejection rejection)
        {
            this.status = status;
            this.rejection = rejection;
        }

        private void emit(
            ModelEvent... events)
        {
            this.events.addAll(List.of(events));
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
            this.authorization = authorization;

            now += tick;

            final ModelSource source = new ModelSource()
            {
                @Override
                public String getPath()
                {
                    return "$.field";
                }

                @Override
                public DirectBufferEx getValue()
                {
                    return src;
                }
            };

            final ModelSink sink = new ModelSink()
            {
                @Override
                public ModelStatus transform(
                    ModelController control,
                    ModelSource source,
                    ModelEvent event)
                {
                    return ModelStatus.OK;
                }

                @Override
                public boolean identity()
                {
                    return true;
                }
            };

            for (ModelEvent event : events)
            {
                transform.transform(control, source, event, sink);
            }

            return result.set(status, srcLimit - srcIndex, 0, rejection);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }

        @Override
        public int padding(
            DirectBufferEx data,
            int index,
            int length)
        {
            return 7;
        }

        @Override
        public void reset()
        {
            resets++;
        }
    }

    private static final class Passing implements ModelTransform
    {
        private final List<ModelEvent> events = new ArrayList<>();

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            events.add(event);
            return sink.transform(control, source, event);
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }
}
