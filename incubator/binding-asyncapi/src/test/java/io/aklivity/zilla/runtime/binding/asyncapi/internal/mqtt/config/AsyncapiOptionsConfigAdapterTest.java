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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.mqtt.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncApi;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttCredentialsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.internal.config.OptionsAdapter;
import io.aklivity.zilla.runtime.engine.test.internal.validator.config.TestValidatorConfig;

public class AsyncapiOptionsConfigAdapterTest
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
        String content;
        try (InputStream resource = AsyncapiOptionsConfigAdapterTest.class
            .getResourceAsStream("../../../../../../specs/binding/asyncapi/config/files/mqtt/asyncapi.yaml"))
        {
            content = new String(resource.readAllBytes(), UTF_8);
        }
        Mockito.doReturn(content).when(context).readURL("mqtt/asyncapi.yaml");

        adapter = new OptionsAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("asyncapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"specs\":" +
                    "[" +
                        "\"mqtt/asyncapi.yaml\"" +
                    "]" +
                "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));

        AsyncapiConfig asyncapi = options.asyncapis.get(0);
        assertThat(asyncapi.location, equalTo("mqtt/asyncapi.yaml"));
        assertThat(asyncapi.asyncApi, instanceOf(AsyncApi.class));
    }

    @Test
    public void shouldWriteOptions()
    {
        List<AsyncapiConfig> asyncapis = new ArrayList<>();
        asyncapis.add(new AsyncapiConfig("mqtt/asyncapi.yaml", new AsyncApi()));

        AsyncapiOptionsConfig options = new AsyncapiOptionsConfig(asyncapis);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"specs\":" +
                    "[" +
                        "\"mqtt/asyncapi.yaml\"" +
                    "]" +
                "}"));
    }
}
