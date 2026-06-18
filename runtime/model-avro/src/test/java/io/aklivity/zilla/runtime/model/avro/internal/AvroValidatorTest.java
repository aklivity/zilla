/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.runtime.engine.model.ValidatorHandler.FLAGS_FIN;
import static io.aklivity.zilla.runtime.engine.model.ValidatorHandler.FLAGS_INIT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroValidatorTest
{
    private static final String SCHEMA = """
        {
          "fields": [
            {
              "name": "id",
              "type": "string"
            },
            {
              "name": "status",
              "type": "string"
            }
          ],
          "name": "Event",
          "namespace": "io.aklivity.example",
          "type": "record"
        }""";

    private EngineContext context;
    private AvroModelConfiguration config;

    @Before
    public void init()
    {
        config = new AvroModelConfiguration(new Configuration());
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldVerifyValidCompleteAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
            .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(1)
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroValidatorHandler validator = new AvroValidatorHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidCompleteAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(1)
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroValidatorHandler validator = new AvroValidatorHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);

        assertFalse(validator.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidFragmentedAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(1)
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroValidatorHandler validator = new AvroValidatorHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(0L, 0L, FLAGS_INIT, data, 0, 10, ValueConsumer.NOP));
        assertTrue(validator.validate(0L, 0L, FLAGS_FIN, data, 10, data.capacity() - 10, ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidFragmentedAvroEvent()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(1)
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroValidatorHandler validator = new AvroValidatorHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(0L, 0L, FLAGS_INIT, data, 0, 3, ValueConsumer.NOP));
        assertFalse(validator.validate(0L, 0L, FLAGS_FIN, data, 3, data.capacity() - 3, ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidCompleteAvroEventWithEncoded()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("encoded")
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroValidatorHandler validator = new AvroValidatorHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        data.wrap(bytes, 0, bytes.length);

        assertTrue(validator.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyInvalidCompleteAvroEventWithEncoded()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("encoded")
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroValidatorHandler validator = new AvroValidatorHandler(config, model, context);

        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0x00, 0x00, 0x00, 0x09, 0x06, 0x69, 0x64, 0x30, 0x10};
        data.wrap(bytes, 0, bytes.length);

        assertFalse(validator.validate(0L, 0L, data, 0, data.capacity(), ValueConsumer.NOP));
    }

    @Test
    public void shouldVerifyValidAvroEventFragmentedPerByte()
    {
        AvroValidatorHandler validator = newValidator();

        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        DirectBuffer data = new UnsafeBuffer(bytes);

        boolean valid = true;
        for (int i = 0; i < bytes.length; i++)
        {
            int flags = 0;
            if (i == 0)
            {
                flags |= FLAGS_INIT;
            }
            if (i == bytes.length - 1)
            {
                flags |= FLAGS_FIN;
            }
            valid = validator.validate(0L, 0L, flags, data, i, 1, ValueConsumer.NOP);
            assertTrue("fragment " + i + " rejected", valid);
        }
    }

    @Test
    public void shouldForwardValidatedBytesToConsumer()
    {
        AvroValidatorHandler validator = newValidator();

        Capture capture = new Capture();
        byte[] bytes = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        DirectBuffer data = new UnsafeBuffer(bytes);

        assertTrue(validator.validate(0L, 0L, data, 0, bytes.length, capture));
        assertArrayEquals(bytes, capture.bytes());
    }

    @Test
    public void shouldForwardValidatedBytesIncrementallyWhenFragmented()
    {
        AvroValidatorHandler validator = newValidator();

        Capture capture = new Capture();
        byte[] head = {0x06, 0x69, 0x64, 0x30, 0x10};
        byte[] tail = {0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};

        assertTrue(validator.validate(0L, 0L, FLAGS_INIT, new UnsafeBuffer(head), 0, head.length, capture));
        int forwardedAfterInit = capture.length;
        assertTrue(validator.validate(0L, 0L, FLAGS_FIN, new UnsafeBuffer(tail), 0, tail.length, capture));

        assertTrue("expected bytes forwarded before the final fragment", forwardedAfterInit > 0);
        byte[] whole = {0x06, 0x69, 0x64,
            0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        assertArrayEquals(whole, capture.bytes());
    }

    @Test
    public void shouldForwardEarlierFragmentsThenRejectInvalidLater()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroValidatorHandler validator = newValidator();

        Capture capture = new Capture();
        byte[] head = {0x06, 0x69, 0x64, 0x30};
        byte[] tail = {0x10};

        assertTrue(validator.validate(0L, 0L, FLAGS_INIT, new UnsafeBuffer(head), 0, head.length, capture));
        assertFalse(validator.validate(0L, 0L, FLAGS_FIN, new UnsafeBuffer(tail), 0, tail.length, capture));

        assertTrue("earlier valid fragment should have been forwarded", capture.length > 0);
    }

    private AvroValidatorHandler newValidator()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(1)
                    .build()
                .build()
            .build();

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new AvroValidatorHandler(config, model, context);
    }

    private static final class Capture implements ValueConsumer
    {
        private final MutableDirectBuffer buffer = new ExpandableDirectByteBuffer();

        private int length;

        @Override
        public void accept(
            DirectBuffer data,
            int index,
            int length)
        {
            buffer.putBytes(this.length, data, index, length);
            this.length += length;
        }

        private byte[] bytes()
        {
            byte[] bytes = new byte[length];
            buffer.getBytes(0, bytes);
            return bytes;
        }
    }
}
