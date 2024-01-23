/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.types.protobuf.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterContext;
import io.aklivity.zilla.runtime.engine.converter.ConverterFactory;
import io.aklivity.zilla.runtime.types.protobuf.config.ProtobufConverterConfig;

public class ProtobufConverterFactorySpiTest
{
    @Test
    public void shouldCreateReader()
    {
        Configuration config = new Configuration();
        ConverterFactory factory = ConverterFactory.instantiate();
        Converter converter = factory.create("protobuf", config);

        ConverterContext context = new ProtobufConverterContext(mock(EngineContext.class));

        ConverterConfig converterConfig = ProtobufConverterConfig.builder()
            .subject("test-value")
                .catalog()
                    .name("test0")
                        .schema()
                        .subject("subject1")
                        .version("latest")
                        .build()
                .build()
            .build();

        assertThat(converter, instanceOf(ProtobufConverter.class));
        assertThat(context.supplyReadHandler(converterConfig), instanceOf(ProtobufConverterHandler.class));
        assertThat(context.supplyWriteHandler(converterConfig), instanceOf(ProtobufConverterHandler.class));
    }
}
