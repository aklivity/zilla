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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveKafkaConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveKafkaPropertiesConfig;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class RisingwaveOptionsConfigAdapterTest
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
        adapter.adaptType("risingwave");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            """
                {\
                  "kafka": {
                    "properties": {
                      "bootstrap.server": "localhost:9092"
                    },
                    "format": {
                      "model": "test",
                      "length": 0,
                      "catalog": {
                        "test0": [
                          {
                            "id": 1
                          }
                        ]
                      }
                    }
                  }
                }""";

        RisingwaveOptionsConfig options = jsonb.fromJson(text, RisingwaveOptionsConfig.class);

        assertThat(options, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected = "{\"kafka\":{\"properties\":{\"bootstrap.server\":\"localhost:9092\"},\"format\":\"test\"}}";

        RisingwaveKafkaPropertiesConfig properties = RisingwaveKafkaPropertiesConfig.builder()
            .inject(identity())
            .bootstrapServer("localhost:9092")
            .build();

        TestModelConfig model = TestModelConfig.builder()
            .inject(identity())
            .length(0)
            .catalog(CatalogedConfig.builder().name("tes0t").build())
            .build();

        RisingwaveKafkaConfig kafka = RisingwaveKafkaConfig.builder()
            .inject(identity())
            .properties(properties)
            .format(model)
            .build();


        RisingwaveOptionsConfig options = RisingwaveOptionsConfig.builder()
            .inject(identity())
            .kafka(kafka)
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertEquals(expected, text);
    }
}
