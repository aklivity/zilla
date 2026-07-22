/*
 * Copyright 2021-2026 Aklivity Inc
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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;

public class InlineCatalogHandlerTest
{
    private static final String SCHEMA_A = "{\"type\":\"object\",\"required\":[\"a\"]}";
    private static final String SCHEMA_B = "{\"type\":\"object\",\"required\":[\"b\"]}";

    private InlineCatalogHandler newHandler()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        return new InlineCatalogHandler(options);
    }

    @Test
    public void shouldRegisterAndResolveSchema()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);

        assertThat(schemaId, not(equalTo(NO_SCHEMA_ID)));
        assertThat(handler.resolve(schemaId), equalTo(SCHEMA_A));
        assertThat(handler.resolve("weather", "latest"), equalTo(schemaId));
    }

    @Test
    public void shouldConstructWithoutOptionsAndAcceptRuntimeRegistration()
    {
        InlineCatalogHandler handler = new InlineCatalogHandler(null);

        int schemaId = handler.register("weather", SCHEMA_A);

        assertThat(schemaId, not(equalTo(NO_SCHEMA_ID)));
        assertThat(handler.resolve(schemaId), equalTo(SCHEMA_A));
    }

    @Test
    public void shouldContentAddressIdenticalSchemas()
    {
        InlineCatalogHandler handler = newHandler();

        int first = handler.register("weather", SCHEMA_A);
        int second = handler.register("forecast", SCHEMA_A);

        assertThat(second, equalTo(first));
    }

    @Test
    public void shouldNotBumpReferenceWhenReregisteringSameContent()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);
        handler.register("weather", SCHEMA_A);

        handler.unregister("weather");

        assertThat(handler.resolve(schemaId), nullValue());
    }

    @Test
    public void shouldReplaceSchemaWhenSubjectContentChanges()
    {
        InlineCatalogHandler handler = newHandler();

        int idA = handler.register("weather", SCHEMA_A);
        int idB = handler.register("weather", SCHEMA_B);

        assertThat(idB, not(equalTo(idA)));
        assertThat(handler.resolve(idA), nullValue());
        assertThat(handler.resolve(idB), equalTo(SCHEMA_B));
        assertThat(handler.resolve("weather", "latest"), equalTo(idB));
    }

    @Test
    public void shouldRetainSharedSchemaUntilLastUnregister()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);
        handler.register("forecast", SCHEMA_A);

        handler.unregister("weather");
        assertThat(handler.resolve(schemaId), equalTo(SCHEMA_A));

        handler.unregister("forecast");
        assertThat(handler.resolve(schemaId), nullValue());
    }

    @Test
    public void shouldReturnRemovedSchemaIdOnUnregister()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);
        int[] removed = handler.unregister("weather");

        assertThat(removed.length, equalTo(1));
        assertThat(removed[0], equalTo(schemaId));
    }

    @Test
    public void shouldReturnEmptyOnUnregisterUnknownSubject()
    {
        InlineCatalogHandler handler = newHandler();

        int[] removed = handler.unregister("missing");

        assertThat(removed.length, equalTo(0));
    }

    @Test
    public void shouldResolveMissingSubjectToNoSchemaId()
    {
        InlineCatalogHandler handler = newHandler();

        assertThat(handler.resolve("missing", "latest"), equalTo(NO_SCHEMA_ID));
    }
}
