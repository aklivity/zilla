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
package io.aklivity.zilla.runtime.model.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.model.core.BytesModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesController;
import io.aklivity.zilla.runtime.model.core.ext.BytesEvent;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtHandler;
import io.aklivity.zilla.runtime.model.core.ext.BytesSink;
import io.aklivity.zilla.runtime.model.core.ext.BytesSource;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransformable;
import io.aklivity.zilla.runtime.model.core.ext.CoreCache;
import io.aklivity.zilla.runtime.model.core.ext.StringController;
import io.aklivity.zilla.runtime.model.core.ext.StringEvent;
import io.aklivity.zilla.runtime.model.core.ext.StringSink;
import io.aklivity.zilla.runtime.model.core.ext.StringSource;
import io.aklivity.zilla.runtime.model.core.ext.StringTransform;

public class CoreExtModelPipelineTest
{
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    @Test
    public void shouldObserveValueDeliveredAcrossFragmentsRatherThanWhole()
    {
        Recorder recorder = new Recorder();
        ModelPipeline pipeline = decoder(stage(recorder));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        // ModelPipelineResult is reused across calls, so read each outcome before driving the next
        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, FLAGS_INIT,
            buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertEquals(ModelStatus.UNDERFLOW, first.status());
        assertEquals(3, first.consumed());
        int produced = first.produced();

        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FLAGS_FIN,
            buffer("de"), 0, 2, dst, produced, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals(2, second.consumed());
        assertEquals("abcde", dst.getStringWithoutLengthUtf8(0, produced + second.produced()));

        // the value was seen a fragment at a time, never accumulated into one whole-value event
        assertEquals(List.of("START_VALUE", "SEGMENT:abc", "SEGMENT:de", "END_VALUE"), recorder.observed);
    }

    @Test
    public void shouldSuspendAndResumeUntilBoundedDestinationDrained()
    {
        ModelPipeline pipeline = decoder(stage(new Recorder()));
        byte[] value = "0123456789".getBytes();
        UnsafeBufferEx src = new UnsafeBufferEx(value);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[4]);

        StringBuilder drained = new StringBuilder();
        int srcAt = 0;
        int flags = FLAGS_COMPLETE;
        int overflows = 0;
        ModelStatus status;
        do
        {
            ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, flags,
                src, srcAt, value.length, dst, 0, dst.capacity());
            status = result.status();
            drained.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
            srcAt += result.consumed();
            flags &= ~FLAGS_INIT;
            overflows += status == ModelStatus.OVERFLOW ? 1 : 0;
        } while (status == ModelStatus.OK || status == ModelStatus.OVERFLOW);

        assertEquals(ModelStatus.COMPLETE, status);
        assertEquals("0123456789", drained.toString());
        assertEquals(value.length, srcAt);
        assertTrue(overflows > 0);
    }

    @Test
    public void shouldApplyChainInOnePassWithoutIntermediateMaterialization()
    {
        // the upstream stage appends '1' to each segment; the downstream stage must see that rewritten
        // segment as each fragment flows, not one whole-value handoff after the last fragment arrives
        Recorder downstream = new Recorder();
        ModelPipeline pipeline = decoder(stage(new Appender('1')), stage(downstream));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[32]);

        int produced = pipeline.transform(0L, 0L, 0L, FLAGS_INIT,
            buffer("ab"), 0, 2, dst, 0, dst.capacity()).produced();
        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FLAGS_FIN,
            buffer("cd"), 0, 2, dst, produced, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, second.status());
        assertEquals("ab1cd1", dst.getStringWithoutLengthUtf8(0, produced + second.produced()));
        assertEquals(List.of("START_VALUE", "SEGMENT:ab1", "SEGMENT:cd1", "END_VALUE"), downstream.observed);
    }

    @Test
    public void shouldDrainValueSubstitutedAtValueEndWithoutRerunningFinalChecks()
    {
        // a stage whose output is a function of the whole value emits it at value end; against a bounded
        // destination it keeps draining after the last input byte was consumed, and the model's final
        // checks must reach the validator once across that run, not once per draining call
        Validations validations = new Validations();
        StringExtModelPipeline pipeline = new StringExtModelPipeline(
            new CoreModelHandler(mock(EngineContext.class), StringModel.NAME, () -> validations, false, false),
            validations, false, List.of(new Substitute("0123456789")), ModelEnvelope.NONE, 10);

        byte[] value = "abc".getBytes();
        UnsafeBufferEx src = new UnsafeBufferEx(value);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[4]);

        StringBuilder drained = new StringBuilder();
        int srcAt = 0;
        int flags = FLAGS_COMPLETE;
        ModelStatus status;
        do
        {
            ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, flags,
                src, srcAt, value.length, dst, 0, dst.capacity());
            status = result.status();
            drained.append(dst.getStringWithoutLengthUtf8(0, result.produced()));
            srcAt += result.consumed();
            flags &= ~FLAGS_INIT;
        } while (status == ModelStatus.OK || status == ModelStatus.OVERFLOW);

        assertEquals(ModelStatus.COMPLETE, status);
        assertEquals("0123456789", drained.toString());
        assertEquals(1, validations.initial);
        assertEquals(1, validations.finished);
    }

    @Test
    public void shouldWithholdValueWithoutReportingFailure()
    {
        List<String> reported = new ArrayList<>();
        ModelPipeline pipeline = decoder(engine(reported), stage(new Terminator(true, null)));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            buffer("secret"), 0, 6, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(0, result.produced());
        assertEquals(List.of(), reported);
    }

    @Test
    public void shouldRejectValueWithDiagnostic()
    {
        List<String> reported = new ArrayList<>();
        ModelPipeline pipeline = decoder(engine(reported), stage(new Terminator(false, "unacceptable")));
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            buffer("secret"), 0, 6, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(0, result.produced());
        assertEquals(List.of("A message payload failed validation. A field was not the expected type (unacceptable)."),
            reported);
    }

    @Test
    public void shouldApplyExtensionOnEncodeDirection()
    {
        BytesModelExtHandler ext = new BytesModelExtHandler()
        {
            @Override
            public <T extends BytesTransformable<T>> T encode(
                T stream)
            {
                return stream.transform(new Appender('!'));
            }
        };

        ModelHandler handler = handler(mock(EngineContext.class), ext);
        ModelPipeline pipeline = handler.supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("abc!", dst.getStringWithoutLengthUtf8(0, result.produced()));
        assertFalse(pipeline.identity());
    }

    @Test
    public void shouldLeaveEncodeUnchangedWhenExtensionOverridesDecodeOnly()
    {
        BytesModelExtHandler ext = new BytesModelExtHandler()
        {
            @Override
            public <T extends BytesTransformable<T>> T decode(
                T stream,
                CoreCache cache)
            {
                return stream.transform(new Appender('!'));
            }
        };

        ModelHandler handler = handler(mock(EngineContext.class), ext);
        ModelPipeline pipeline = handler.supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("abc", dst.getStringWithoutLengthUtf8(0, result.produced()));
        // the encode direction is exactly what it would be with no extension installed at all
        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldPreserveIdentityFastPathWhenNoStageInstalled()
    {
        // an installed extension that inserts nothing leaves both directions the plain identity pipeline
        BytesModelExtHandler ext = new BytesModelExtHandler()
        {
            @Override
            public <T extends BytesTransformable<T>> T decode(
                T stream,
                CoreCache cache)
            {
                return stream.transform(BytesTransform.NONE);
            }
        };

        ModelHandler handler = handler(mock(EngineContext.class), ext);

        assertTrue(handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE).identity());
        assertTrue(handler.supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE).identity());
    }

    @Test
    public void shouldReportSummedExtensionPaddingPerDirection()
    {
        ModelHandler handler = handler(mock(EngineContext.class), padded(4, 1), padded(6, 2));
        DirectBufferEx empty = new UnsafeBufferEx(new byte[0]);

        assertEquals(10, handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE).padding(empty, 0, 0));
        assertEquals(3, handler.supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE).padding(empty, 0, 0));
    }

    @Test
    public void shouldReachMetadataEnvelopeFromStage()
    {
        Envelopes envelopes = new Envelopes();
        ModelEnvelope envelope = new TestEnvelope();
        ModelHandler handler = handler(mock(EngineContext.class), stage(envelopes));
        ModelPipeline pipeline = handler.supplyDecoder(envelope, ModelTransform.NONE, ModelCache.NONE);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE, buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertSame(envelope, envelopes.observed);
    }

    @Test
    public void shouldReachAuthorizationFromStage()
    {
        Authorizations authorizations = new Authorizations();
        ModelHandler handler = handler(mock(EngineContext.class), stage(authorizations));
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[16]);

        pipeline.transform(0L, 0L, 42L, FLAGS_COMPLETE, buffer("abc"), 0, 3, dst, 0, dst.capacity());

        assertEquals(42L, authorizations.observed);
    }

    private static ModelPipeline decoder(
        BytesModelExtHandler... exts)
    {
        return decoder(mock(EngineContext.class), exts);
    }

    private static ModelPipeline decoder(
        EngineContext context,
        BytesModelExtHandler... exts)
    {
        return handler(context, exts).supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);
    }

    private static ModelHandler handler(
        EngineContext context,
        BytesModelExtHandler... exts)
    {
        List<BytesModelExtContext> contexts = new ArrayList<>();
        for (BytesModelExtHandler ext : exts)
        {
            contexts.add(config -> ext);
        }

        BytesModelContext model = new BytesModelContext(context, contexts);
        return model.supplyHandler(BytesModelConfig.builder().build());
    }

    private static BytesModelExtHandler stage(
        BytesTransform transform)
    {
        return new BytesModelExtHandler()
        {
            @Override
            public <T extends BytesTransformable<T>> T decode(
                T stream,
                CoreCache cache)
            {
                return stream.transform(transform);
            }
        };
    }

    private static BytesModelExtHandler padded(
        int decodePadding,
        int encodePadding)
    {
        return new BytesModelExtHandler()
        {
            @Override
            public <T extends BytesTransformable<T>> T decode(
                T stream,
                CoreCache cache)
            {
                return stream.transform(new Appender('.'));
            }

            @Override
            public <T extends BytesTransformable<T>> T encode(
                T stream)
            {
                return stream.transform(new Appender('.'));
            }

            @Override
            public int decodePadding()
            {
                return decodePadding;
            }

            @Override
            public int encodePadding()
            {
                return encodePadding;
            }
        };
    }

    private static EngineContext engine(
        List<String> reported)
    {
        EngineContext context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());

        CoreModelEventFormatter formatter = new CoreModelEventFormatterFactory().create(new Configuration());
        MessageConsumer writer = (msgTypeId, buffer, index, length) ->
        {
            MutableDirectBufferEx copy = new UnsafeBufferEx(new byte[length]);
            copy.putBytes(0, buffer, index, length);
            reported.add(formatter.format(copy, 0, length));
        };
        when(context.supplyEventWriter()).thenReturn(writer);

        return context;
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes());
    }

    // records every event as it flows, so a test can assert the value was seen streaming rather than whole
    private static final class Recorder implements BytesTransform
    {
        private final List<String> observed = new ArrayList<>();

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            if (event.segmented())
            {
                DirectBufferEx segment = source.getSegment();
                observed.add("SEGMENT:" + segment.getStringWithoutLengthUtf8(0, segment.capacity()));
            }
            else
            {
                observed.add(event.name());
            }
            return sink.transform(control, source, event);
        }
    }

    // reads the envelope in force and remembers it, so a test can assert a stage reaches the metadata
    // channel through its control handle
    private static final class Envelopes implements BytesTransform
    {
        private ModelEnvelope observed;

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            observed = control.envelope();
            return sink.transform(control, source, event);
        }
    }

    // reads the authorization in force and remembers it, so a test can assert a stage reaches the
    // authorization the pipeline received through its control handle
    private static final class Authorizations implements BytesTransform
    {
        private long observed;

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            observed = control.authorization();
            return sink.transform(control, source, event);
        }
    }

    // appends one byte to every segment as it flows, rewriting the segment in place rather than handing
    // the downstream a separate event, so a chain of these proves stage order and value expansion
    private static final class Appender implements BytesTransform
    {
        private final byte value;
        private final ExpandableArrayBufferEx scratch;
        private final UnsafeBufferEx view;
        private final BytesSource injected;

        private Appender(
            char value)
        {
            this.value = (byte) value;
            this.scratch = new ExpandableArrayBufferEx();
            this.view = new UnsafeBufferEx(new byte[0]);
            this.injected = () -> view;
        }

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            BytesSource downstream = source;
            if (event.segmented())
            {
                DirectBufferEx segment = source.getSegment();
                int length = segment.capacity();
                scratch.putBytes(0, segment, 0, length);
                scratch.putByte(length, value);
                view.wrap(scratch, 0, length + 1);
                downstream = injected;
            }
            return sink.transform(control, downstream, event);
        }
    }

    // terminates the value at its first segment, either withholding it or rejecting it with a diagnostic
    private static final class Terminator implements BytesTransform
    {
        private final boolean withhold;
        private final String diagnostic;

        private Terminator(
            boolean withhold,
            String diagnostic)
        {
            this.withhold = withhold;
            this.diagnostic = diagnostic;
        }

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            ModelStatus status = ModelStatus.OK;
            if (event.segmented())
            {
                if (withhold)
                {
                    control.withhold();
                }
                else
                {
                    control.reject(diagnostic);
                }
                status = ModelStatus.REJECTED;
            }
            else if (event == BytesEvent.START_VALUE)
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }

    // drops the value's own bytes and emits its substitute once, at value end -- the shape of any stage
    // whose output is a function of the whole value
    private static final class Substitute implements StringTransform
    {
        private final UnsafeBufferEx value;
        private final UnsafeBufferEx view;
        private final StringSource injected;
        private final Absorbing absorbing;

        private int offset;

        private Substitute(
            String value)
        {
            this.value = new UnsafeBufferEx(value.getBytes());
            this.view = new UnsafeBufferEx(new byte[0]);
            this.injected = () -> view;
            this.absorbing = new Absorbing();
        }

        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event,
            StringSink sink)
        {
            ModelStatus status;
            if (event == StringEvent.START_VALUE)
            {
                offset = 0;
                status = sink.transform(control, source, event);
            }
            else if (event.segmented())
            {
                status = ModelStatus.OK;
            }
            else
            {
                status = emit(control, source, sink, false);
            }
            return status;
        }

        @Override
        public ModelStatus resume(
            StringController control,
            StringSource source,
            StringEvent event,
            StringSink sink)
        {
            return event == StringEvent.END_VALUE
                ? emit(control, source, sink, true)
                : sink.resume(control, source, event);
        }

        private ModelStatus emit(
            StringController control,
            StringSource source,
            StringSink sink,
            boolean resuming)
        {
            ModelStatus status = ModelStatus.OK;
            if (offset < value.capacity())
            {
                view.wrap(value, offset, value.capacity() - offset);
                absorbing.wrap(control, this);
                status = resuming
                    ? sink.resume(absorbing, injected, StringEvent.SEGMENT)
                    : sink.transform(absorbing, injected, StringEvent.SEGMENT);
                if (status == ModelStatus.OK)
                {
                    offset = value.capacity();
                }
            }

            if (status == ModelStatus.OK)
            {
                status = sink.transform(control, source, StringEvent.END_VALUE);
            }
            return status;
        }
    }

    // the control handle a stage supplies downstream for bytes it injected: those bytes are its own, not
    // the upstream's, so a report of them advances its output cursor rather than the upstream's
    private static final class Absorbing implements StringController
    {
        private StringController delegate;
        private Substitute owner;

        private void wrap(
            StringController delegate,
            Substitute owner)
        {
            this.delegate = delegate;
            this.owner = owner;
        }

        @Override
        public ModelEnvelope envelope()
        {
            return delegate.envelope();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            owner.offset += sourceBytes;
        }

        @Override
        public void reject(
            String diagnostic)
        {
            delegate.reject(diagnostic);
        }

        @Override
        public void withhold()
        {
            delegate.withhold();
        }
    }

    // counts the INIT and FIN fragments the model's own validation sees across a value
    private static final class Validations implements CoreModelValidator
    {
        private int initial;
        private int finished;

        @Override
        public Validity validate(
            int flags,
            DirectBufferEx data,
            int index,
            int length)
        {
            initial += (flags & CoreModelValidator.FLAGS_INIT) != 0 ? 1 : 0;
            finished += (flags & CoreModelValidator.FLAGS_FIN) != 0 ? 1 : 0;
            return Validity.VALID;
        }
    }

    private static final class TestEnvelope implements ModelEnvelope
    {
        @Override
        public int count(
            String name)
        {
            return 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            return null;
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
        }
    }
}
