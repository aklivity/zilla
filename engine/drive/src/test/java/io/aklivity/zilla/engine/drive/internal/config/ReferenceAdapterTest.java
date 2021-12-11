/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.engine.drive.internal.config;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class ReferenceAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamspaceRefAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadReference()
    {
        String text =
                "{" +
                    "\"name\": \"test\"" +
                "}";

        NamespaceRef ref = jsonb.fromJson(text, NamespaceRef.class);

        assertThat(ref, not(nullValue()));
        assertThat(ref.name, equalTo("test"));
        assertThat(ref.links, equalTo(emptyMap()));
    }


    @Test
    public void shouldWriteReference()
    {
        NamespaceRef route = new NamespaceRef("test", emptyMap());

        String text = jsonb.toJson(route);

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

        NamespaceRef ref = jsonb.fromJson(text, NamespaceRef.class);

        assertThat(ref, not(nullValue()));
        assertThat(ref.name, equalTo("test"));
        assertThat(ref.links, equalTo(singletonMap("self", "/test")));
    }


    @Test
    public void shouldWriteReferenceWithLink()
    {
        NamespaceRef route = new NamespaceRef("test", singletonMap("self", "/test"));

        String text = jsonb.toJson(route);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"links\":{\"self\":\"/test\"}}"));
    }
}
