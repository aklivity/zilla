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

import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

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
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("openapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            "{" +
            "\"specs\": {" +
            "    \"petstore\": {" +
            "      \"catalog\": {" +
            "        \"catalog0\": {" +
            "          \"subject\": \"petstore\"," +
            "          \"version\": \"latest\"" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }," +
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
            "    }" +
            "  }";

        OpenapiOptionsConfig options = jsonb.fromJson(text, OpenapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected = "{\"tcp\":{\"host\":\"localhost\",\"port\":8080},\"tls\":{\"sni\":[\"example.net\"]}," +
            "\"specs\":{\"test\":{\"catalog\":{\"catalog0\":{\"subject\":\"petstore\",\"version\":\"latest\"}}}}}";

        TcpOptionsConfig tcp = TcpOptionsConfig.builder()
            .inject(identity())
            .host("localhost")
            .ports(new int[] { 8080 })
            .build();

        TlsOptionsConfig tls = TlsOptionsConfig.builder()
            .inject(identity())
            .sni(asList("example.net"))
            .build();

        List<OpenapiConfig> spec = new ArrayList<>();
        spec.add(new OpenapiConfig("test",
            List.of(new OpenapiCatalogConfig("catalog0", "petstore", "latest"))));

        OpenapiOptionsConfig options = new OpenapiOptionsConfig(tcp, tls, null, spec);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }
}
