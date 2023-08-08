/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.config;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.NamespaceRefConfig;

public class NamespaceRefConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private ConfigAdapterContext context;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamspaceRefAdapter(context));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadReference()
    {
        String text =
                "{" +
                    "\"name\": \"test\"" +
                "}";

        NamespaceRefConfig ref = jsonb.fromJson(text, NamespaceRefConfig.class);

        assertThat(ref, not(nullValue()));
        assertThat(ref.name, equalTo("test"));
        assertThat(ref.links, equalTo(emptyMap()));
    }


    @Test
    public void shouldWriteReference()
    {
        NamespaceRefConfig reference = NamespaceRefConfig.builder()
                .name("test")
                .build();

        String text = jsonb.toJson(reference);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\"}"));
    }

    @Test
    public void shouldReadReferenceWithLink()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"links\":" +
                    "{" +
                      "\"self\": \"/test\"" +
                    "}" +
                "}";

        NamespaceRefConfig ref = jsonb.fromJson(text, NamespaceRefConfig.class);

        assertThat(ref, not(nullValue()));
        assertThat(ref.name, equalTo("test"));
        assertThat(ref.links, equalTo(singletonMap("self", "/test")));
    }


    @Test
    public void shouldWriteReferenceWithLink()
    {
        NamespaceRefConfig reference = NamespaceRefConfig.builder()
                .name("test")
                .link("self", "/test")
                .build();

        String text = jsonb.toJson(reference);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"links\":{\"self\":\"/test\"}}"));
    }
}
