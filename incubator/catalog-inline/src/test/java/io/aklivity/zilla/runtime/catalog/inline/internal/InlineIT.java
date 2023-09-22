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
package io.aklivity.zilla.runtime.catalog.inline.internal;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.inline.internal.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.internal.config.InlineSchemaConfig;

public class InlineIT
{
    private InlineOptionsConfig config;

    @Before
    public void setup()
    {
        config = new InlineOptionsConfig(singletonList(
                new InlineSchemaConfig("subject1", "latest",
                        "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                        "{\"name\":\"status\",\"type\":\"string\"}]," +
                        "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}")));
    }

    @Test
    public void shouldResolveSchemaViaSchemaId()
    {
        String expected = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
                "{\"name\":\"status\",\"type\":\"string\"}]," +
                "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

        InlineCatalogHandler catalog = new InlineCatalogHandler(config);

        int schemaId = catalog.resolve("subject1", "latest");
        String schema = catalog.resolve(schemaId);

        assertThat(schema, not(nullValue()));
        assertEquals(expected, schema);
    }
}
