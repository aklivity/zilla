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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.internal.config.OptionsAdapter;

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
            .getResourceAsStream("../../../../../specs/binding/asyncapi/config/mqtt/asyncapi.yaml"))
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
                    "]," +
                    "\"host\": \"localhost\"," +
                    "\"port\": 7183," +
                    "\"keys\":" +
                    "[" +
                        "\"localhost\"" +
                    "]," +
                    "\"trust\":" +
                    "[" +
                        "\"serverca\"" +
                    "]," +
                    "\"trustcacerts\":true," +
                    "\"sni\":" +
                    "[" +
                        "\"example.net\"" +
                    "]," +
                    "\"alpn\":" +
                    "[" +
                        "\"echo\"" +
                    "]" +
                "}";

        AsyncapiOptionsConfig options = jsonb.fromJson(text, AsyncapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        AsyncapiConfig asyncapi = options.specs.get(0);
        assertThat(asyncapi.location, equalTo("mqtt/asyncapi.yaml"));
        assertThat(asyncapi.asyncApi, instanceOf(Asyncapi.class));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.ports.length, equalTo(1));
        assertThat(options.ports[0], equalTo(7183));
        assertThat(options.keys, equalTo(asList("localhost")));
        assertThat(options.trust, equalTo(asList("serverca")));
        assertThat(options.trustcacerts, equalTo(true));
        assertThat(options.sni, equalTo(asList("example.net")));
        assertThat(options.alpn, equalTo(asList("echo")));
    }

    @Test
    public void shouldWriteOptions()
    {
        List<AsyncapiConfig> specs = new ArrayList<>();
        specs.add(new AsyncapiConfig("mqtt/asyncapi.yaml", new Asyncapi()));


        AsyncapiOptionsConfig options = AsyncapiOptionsConfig.builder()
            .inject(Function.identity())
            .specs(specs)
            .host("localhost")
            .ports(new int[] { 7183 })
            .keys(asList("localhost"))
            .trust(asList("serverca"))
            .sni(asList("example.net"))
            .alpn(asList("echo"))
            .trustcacerts(true)
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{" +
                "\"specs\":" +
                    "[" +
                        "\"mqtt/asyncapi.yaml\"" +
                    "]," +
                    "\"host\":\"localhost\"," +
                    "\"port\":7183," +
                    "\"keys\":" +
                    "[" +
                        "\"localhost\"" +
                    "]," +
                    "\"trust\":" +
                    "[" +
                        "\"serverca\"" +
                    "]," +
                    "\"trustcacerts\":true," +
                    "\"sni\":" +
                    "[" +
                        "\"example.net\"" +
                    "]," +
                    "\"alpn\":" +
                    "[" +
                        "\"echo\"" +
                    "]" +
                "}"));
    }
}
