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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

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

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.PathItem;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.specs.binding.openapi.OpenapiSpecs;

public class OpenapiOptionsConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ConfigAdapterContext context;

    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        try (InputStream resource = OpenapiSpecs.class.getResourceAsStream("config/openapi/petstore.yaml"))
        {
            String content = new String(resource.readAllBytes(), UTF_8);
            Mockito.doReturn(content).when(context).readURL("openapi/petstore.yaml");
            OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
            adapter.adaptType("openapi");
            JsonbConfig config = new JsonbConfig()
                .withAdapters(adapter);
            jsonb = JsonbBuilder.create(config);
        }
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
            "    \"tls\": {" +
            "      \"keys\": [" +
            "        \"localhost\"" +
            "      ]," +
            "      \"alpn\": [" +
            "        \"localhost\"" +
            "      ]" +
            "    }," +
            "    \"http\": {" +
            "      \"authorization\": {" +
            "        \"test0\": {" +
            "          \"credentials\": {" +
            "            \"cookies\": {" +
            "              \"access_token\": \"{credentials}\"" +
            "            }," +
            "            \"headers\": {" +
            "              \"authorization\": \"Bearer {credentials}\"" +
            "            }," +
            "            \"query\": {" +
            "              \"access_token\": \"{credentials}\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    }," +
            "    \"specs\": [" +
            "      \"openapi/petstore.yaml\"" +
            "    ]" +
            "  }";

        OpenapiOptionsConfig options = jsonb.fromJson(text, OpenapiOptionsConfig.class);
        OpenapiConfig openapi = options.openapis.stream().findFirst().get();
        PathItem path = openapi.openapi.paths.get("/pets");

        assertThat(options, not(nullValue()));
        assertThat(path.post, not(nullValue()));
        assertThat(options.tls, not(nullValue()));
        assertThat(options.http, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        OpenapiOptionsConfig options = new OpenapiOptionsConfig(null, null, asList(
            new OpenapiConfig("openapi/petstore.yaml", new OpenApi())));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals("{\"specs\":[\"openapi/petstore.yaml\"]}", text);
    }
}
