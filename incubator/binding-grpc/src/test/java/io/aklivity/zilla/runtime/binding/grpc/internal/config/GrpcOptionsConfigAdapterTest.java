/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.internal.config.OptionsAdapter;

public class GrpcOptionsConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ConfigAdapterContext context;

    private OptionsAdapter adapter;

    private Jsonb jsonb;


    @Before
    public void initJson() throws IOException
    {
        Path resources = Path.of("src/test/resources/proto");
        Path file = resources.resolve("echo.proto");
        String content = Files.readString(file);
        Mockito.doReturn(content).when(context).readURL("protobuf/echo.proto");
        adapter = new OptionsAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("grpc");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
                "\"services\": [\"protobuf/echo.proto\"]" +
            "}";

        GrpcOptionsConfig options = jsonb.fromJson(text, GrpcOptionsConfig.class);

        assertThat(options, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        GrpcOptionsConfig options = new GrpcOptionsConfig(Arrays.asList(new GrpcProtobufConfig("protobuf/echo.proto")));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
    }
}
