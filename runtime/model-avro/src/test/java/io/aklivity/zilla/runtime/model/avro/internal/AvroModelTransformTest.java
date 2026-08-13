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
package io.aklivity.zilla.runtime.model.avro.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;

public class AvroModelTransformTest
{
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
        {
            "fields":
            [
                { "name": "id", "type": "string" },
                { "name": "status", "type": "string" }
            ],
            "name": "Event",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final String SCALARS_SCHEMA = """
        {
            "fields":
            [
                { "name": "i", "type": "int" },
                { "name": "l", "type": "long" },
                { "name": "f", "type": "float" },
                { "name": "d", "type": "double" },
                { "name": "b", "type": "boolean" },
                { "name": "e", "type": { "type": "enum", "name": "Kind", "symbols": [ "LOW", "HIGH" ] } }
            ],
            "name": "Scalars",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    // id="id0" (len 3) then status="positive" (len 8)
    private static final byte[] AVRO = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};

    private static final String NESTED_SCHEMA = """
        {
            "fields":
            [
                { "name": "id", "type": "string" },
                { "name": "user", "type":
                    { "type": "record", "name": "User", "fields":
                        [ { "name": "ssn", "type": "string" } ] } }
            ],
            "name": "Envelope",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    // id="a" (len 1) then user.ssn="ssn0" (len 4)
    private static final byte[] NESTED = {0x02, 0x61, 0x08, 0x73, 0x73, 0x6e, 0x30};

    private static final String MIXED_SCHEMA = """
        {
            "fields":
            [
                { "name": "u", "type": [ "null", "string" ] },
                { "name": "y", "type": "bytes" },
                { "name": "x", "type": { "type": "fixed", "name": "Four", "size": 4 } },
                { "name": "b", "type": "boolean" },
                { "name": "f", "type": "float" },
                { "name": "d", "type": "double" },
                { "name": "n", "type": [ "null",
                    { "type": "record", "name": "Inner", "fields": [ { "name": "v", "type": "int" } ] } ] },
                { "name": "z", "type": [ "null", "string" ] }
            ],
            "name": "Mixed",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    // u=branch 1 "ok", y=bytes{01,02}, x=fixed{0a,0b,0c,0d}, b=true, f=1.5, d=2.5, n=branch 1 {v:3}, z=branch 0 null
    private static final byte[] MIXED =
    {
        0x02, 0x04, 0x6f, 0x6b,
        0x04, 0x01, 0x02,
        0x0a, 0x0b, 0x0c, 0x0d,
        0x01,
        0x00, 0x00, (byte) 0xc0, 0x3f,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x40,
        0x02, 0x06,
        0x00
    };

    // i=5, l=7, f=1.5, d=2.5, b=true, e=index 1
    private static final byte[] SCALARS =
    {
        0x0a,
        0x0e,
        0x00, 0x00, (byte) 0xc0, 0x3f,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x40,
        0x01,
        0x02
    };

    private static final List<String> FRAMED = List.of(
        "START_VALUE", "FIELD($.id=id0)", "FIELD($.status=positive)", "flush", "END_VALUE");

    private EngineContext context;
    private AvroModelConfiguration config;

    @Before
    public void init()
    {
        config = new AvroModelConfiguration(new Configuration());
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldReplaceStringField()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.id", "replaced"));

        assertEquals("{\"id\":\"replaced\",\"status\":\"positive\"}", decode(pipeline, AVRO));
    }

    @Test
    public void shouldReplaceStringFieldWithLongerValue()
    {
        String longer = "a-much-longer-replacement-value-than-the-original";
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.status", longer));

        assertEquals("{\"id\":\"id0\",\"status\":\"" + longer + "\"}", decode(pipeline, AVRO));
    }

    @Test
    public void shouldDeclineStringField()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Declining("$.status"));

        assertEquals("{\"id\":\"id0\",\"status\":\"\"}", decode(pipeline, AVRO));
    }

    @Test
    public void shouldReplaceScalarField()
    {
        AvroModelHandlerImpl handler = newHandler(SCALARS_SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.l", "99"));

        assertEquals("{\"i\":5,\"l\":99,\"f\":1.5,\"d\":2.5,\"b\":true,\"e\":\"HIGH\"}", decode(pipeline, SCALARS));
    }

    @Test
    public void shouldDeclineScalarFields()
    {
        AvroModelHandlerImpl handler = newHandler(SCALARS_SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Declining("$.i", "$.b", "$.e"));

        assertEquals("{\"i\":0,\"l\":7,\"f\":1.5,\"d\":2.5,\"b\":false,\"e\":\"LOW\"}", decode(pipeline, SCALARS));
    }

    @Test
    public void shouldReplaceNestedScalarField()
    {
        AvroModelHandlerImpl handler = newHandler(NESTED_SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.user.ssn", "replaced"));

        assertEquals("{\"id\":\"a\",\"user\":{\"ssn\":\"replaced\"}}", decode(pipeline, NESTED));
    }

    @Test
    public void shouldDeclineNestedScalarField()
    {
        AvroModelHandlerImpl handler = newHandler(NESTED_SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Declining("$.user.ssn"));

        assertEquals("{\"id\":\"a\",\"user\":{\"ssn\":\"\"}}", decode(pipeline, NESTED));
    }

    @Test
    public void shouldNotDescendMatchingByLeafNameAloneAcrossDepths()
    {
        AvroModelHandlerImpl handler = newHandler(NESTED_SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Declining("$.ssn"));

        assertEquals("{\"id\":\"a\",\"user\":{\"ssn\":\"ssn0\"}}", decode(pipeline, NESTED));
    }

    @Test
    public void shouldForwardEveryFieldUntouchedWhenNoPathMatches()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.absent", "replaced"));

        assertEquals("{\"id\":\"id0\",\"status\":\"positive\"}", decode(pipeline, AVRO));
    }

    @Test
    public void shouldReproduceEveryFieldWhenSubstitutingIdenticalValues()
    {
        AvroModelHandlerImpl handler = newHandler(MIXED_SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(new Echoing());

        assertArrayEquals(MIXED, decodeChunked(pipeline, MIXED, 256));
    }

    @Test
    public void shouldWritePlaceholderForEveryDeclinedField()
    {
        AvroModelHandlerImpl handler = newHandler(MIXED_SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(new Declining("$.u", "$.y", "$.x", "$.b", "$.f", "$.d"));

        byte[] expected =
        {
            0x02, 0x00,
            0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x02, 0x06,
            0x00
        };
        assertArrayEquals(expected, decodeChunked(pipeline, MIXED, 256));
    }

    @Test
    public void shouldResumeAcrossBoundedOutput()
    {
        AvroModelHandlerImpl handler = newHandler(MIXED_SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(new Echoing());

        assertArrayEquals(MIXED, decodeChunked(pipeline, MIXED, 24));
    }

    @Test
    public void shouldStreamSubstituteAcrossBoundedOutput()
    {
        String longer = "a-much-longer-replacement-value-than-the-original";
        AvroModelHandlerImpl handler = newHandler(SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.status", longer));

        byte[] status = longer.getBytes(UTF_8);
        byte[] expected = new byte[5 + status.length];
        expected[0] = 0x06;
        expected[1] = 'i';
        expected[2] = 'd';
        expected[3] = '0';
        // Avro zigzag-encodes the length, so 48 bytes is written as 96
        expected[4] = (byte) (status.length << 1);
        System.arraycopy(status, 0, expected, 5, status.length);

        assertArrayEquals(expected, decodeChunked(pipeline, AVRO, 24));
    }

    @Test
    public void shouldFrameEveryFieldRunWhenObserving()
    {
        Recording recording = new Recording(true);
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");

        decode(handler.supplyDecoder(recording), AVRO);

        assertEquals(FRAMED, recording.events);
    }

    @Test
    public void shouldFrameEveryFieldRunWhenMediating()
    {
        Recording recording = new Recording(false);
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");

        decode(handler.supplyDecoder(recording), AVRO);

        // the pump stops the moment the terminal sink closes the top-level record, so END_MESSAGE never
        // reaches the adapter; the run must still close off the datum completing
        assertEquals(FRAMED, recording.events);
    }

    @Test
    public void shouldRejectValueWhenTransformRejects()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyDecoder(new Rejecting("$.status"));

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldNotReportIdentityWhenMediating()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(new Rewriting("$.id", "replaced"));

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertFalse(pipeline.identity());
    }

    @Test
    public void shouldReportIdentityWhenObserving()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, null);
        ModelPipeline pipeline = handler.supplyDecoder(new Observing());

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertTrue(pipeline.identity());
    }

    @Test
    public void shouldReplaceFieldOnEncode()
    {
        AvroModelHandlerImpl handler = newHandler(SCHEMA, "json");
        ModelPipeline pipeline = handler.supplyEncoder(new Rewriting("$.id", "replaced"));

        byte[] json = "{\"id\":\"id0\",\"status\":\"positive\"}".getBytes(UTF_8);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(json), 0, json.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        byte[] encoded = new byte[result.produced()];
        dst.getBytes(0, encoded);
        assertTrue(new String(encoded, UTF_8).contains("replaced"));
    }

    // drives the pipeline the way a caller does, draining the destination on every OVERFLOW so the adapter
    // has to resume a partially written substitute
    private byte[] decodeChunked(
        ModelPipeline pipeline,
        byte[] avro,
        int window)
    {
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[window]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int srcAt = 0;
        int flags = FLAGS_COMPLETE;
        ModelStatus status = ModelStatus.OK;
        for (int rounds = 0; rounds < 64 && status != ModelStatus.COMPLETE && status != ModelStatus.REJECTED; rounds++)
        {
            ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, flags,
                new UnsafeBufferEx(avro), srcAt, avro.length, dst, 0, dst.capacity());
            status = result.status();
            if (result.produced() > 0)
            {
                byte[] chunk = new byte[result.produced()];
                dst.getBytes(0, chunk);
                out.writeBytes(chunk);
            }
            srcAt += result.consumed();
            flags = FLAGS_FIN;
        }
        assertEquals(ModelStatus.COMPLETE, status);
        return out.toByteArray();
    }

    private String decode(
        ModelPipeline pipeline,
        byte[] avro)
    {
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[512]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(avro), 0, avro.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());

        byte[] chunk = new byte[result.produced()];
        dst.getBytes(0, chunk);
        return new String(chunk, UTF_8);
    }

    private AvroModelHandlerImpl newHandler(
        String schema,
        String view)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(schema)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .view(view)
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new AvroModelHandlerImpl(config, model, context);
    }

    private static boolean matches(
        String[] paths,
        String path)
    {
        boolean matched = false;
        for (int i = 0; !matched && i < paths.length; i++)
        {
            matched = paths[i].equals(path);
        }
        return matched;
    }

    // substitutes a fixed value for one path, exercising the mediating (withhold and re-emit) mode
    private static final class Rewriting implements ModelTransform
    {
        private final String path;
        private final Substitute substitute;

        private Rewriting(
            String path,
            String value)
        {
            this.path = path;
            this.substitute = new Substitute(path, value);
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return event == ModelEvent.FIELD && path.equals(source.getPath())
                ? sink.transform(control, substitute, ModelEvent.REPLACED)
                : sink.transform(control, source, event);
        }
    }

    private static final class Declining implements ModelTransform
    {
        private final String[] paths;

        private Declining(
            String... paths)
        {
            this.paths = paths;
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return event == ModelEvent.FIELD && matches(paths, source.getPath())
                ? sink.transform(control, source, ModelEvent.DECLINED)
                : sink.transform(control, source, event);
        }
    }

    // answers every field with a substitute holding exactly the bytes it received, so a mediating pass must
    // reproduce the input byte for byte
    private static final class Echoing implements ModelTransform
    {
        private final Substitute substitute;

        private Echoing()
        {
            this.substitute = new Substitute();
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return event == ModelEvent.FIELD
                ? sink.transform(control, substitute.copy(source), ModelEvent.REPLACED)
                : sink.transform(control, source, event);
        }
    }

    // records the field run exactly as the adapter delivers it, framing included
    private static final class Recording implements ModelTransform
    {
        private final List<String> events;
        private final boolean identity;

        private Recording(
            boolean identity)
        {
            this.events = new ArrayList<>();
            this.identity = identity;
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            DirectBufferEx value = source.getValue();
            events.add(event == ModelEvent.FIELD
                ? "FIELD(" + source.getPath() + "=" + value.getStringWithoutLengthUtf8(0, value.capacity()) + ")"
                : event.name());
            return sink.transform(control, source, event);
        }

        @Override
        public ModelStatus flush(
            ModelController control,
            ModelSource source,
            ModelSink sink)
        {
            events.add("flush");
            return sink.flush(control, source);
        }

        @Override
        public boolean identity()
        {
            return identity;
        }
    }

    private static final class Rejecting implements ModelTransform
    {
        private final String path;

        private Rejecting(
            String path)
        {
            this.path = path;
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            ModelStatus status;
            if (event == ModelEvent.FIELD && path.equals(source.getPath()))
            {
                control.reject("field " + path + " not permitted");
                status = ModelStatus.REJECTED;
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }
    }

    private static final class Observing implements ModelTransform
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    private static final class Substitute implements ModelSource
    {
        private final MutableDirectBufferEx copy;
        private final UnsafeBufferEx view;

        private String path;
        private DirectBufferEx value;

        private Substitute()
        {
            this.copy = new ExpandableDirectByteBufferEx();
            this.view = new UnsafeBufferEx(new byte[0]);
        }

        private Substitute(
            String path,
            String value)
        {
            this();
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

        private Substitute copy(
            ModelSource source)
        {
            DirectBufferEx incoming = source.getValue();
            int length = incoming.capacity();
            copy.putBytes(0, incoming, 0, length);
            view.wrap(copy, 0, length);
            this.path = source.getPath();
            this.value = view;
            return this;
        }
    }
}
