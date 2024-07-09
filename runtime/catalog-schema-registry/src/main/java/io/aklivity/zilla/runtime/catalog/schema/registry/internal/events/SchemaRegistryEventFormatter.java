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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.events;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.StringFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventExFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryRetrievableSchemaIdExFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryRetrievableSchemaSubjectVersionExFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryUnretrievableSchemaIdExFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryUnretrievableSchemaSubjectVersionExFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryUnretrievableSchemaSubjectVersionStaleSchemaExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class SchemaRegistryEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final SchemaRegistryEventExFW eventExRO = new SchemaRegistryEventExFW();

    public SchemaRegistryEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final SchemaRegistryEventExFW eventEx = event.extension().get(eventExRO::wrap);

        String result = null;
        switch (eventEx.kind())
        {
        case UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION:
        {
            SchemaRegistryUnretrievableSchemaSubjectVersionExFW ex = eventEx.unretrievableSchemaSubjectVersion();
            result = String.format(
                    "Unable to fetch schema for subject %s with version %s.",
                    asString(ex.subject()),
                    asString(ex.version())
            );
            break;
        }
        case UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION_STALE_SCHEMA:
        {
            SchemaRegistryUnretrievableSchemaSubjectVersionStaleSchemaExFW ex = eventEx
                .unretrievableSchemaSubjectVersionStaleSchema();
            result = String.format(
                    "Unable to fetch schema for subject %s with version %s; using stale schema with id %d.",
                    asString(ex.subject()),
                    asString(ex.version()),
                    ex.schemaId()
            );
            break;
        }
        case UNRETRIEVABLE_SCHEMA_ID:
        {
            SchemaRegistryUnretrievableSchemaIdExFW ex = eventEx.unretrievableSchemaId();
            result = String.format("Unable to fetch schema id %d.", ex.schemaId());
            break;
        }
        case RETRIEVED_SCHEMA_SUBJECT_VERSION:
        {
            SchemaRegistryRetrievableSchemaSubjectVersionExFW ex = eventEx.retrievableSchemaSubjectVersion();
            result = String.format("Successfully fetched schema for subject %s with version %s.",
                    asString(ex.subject()),
                    asString(ex.version())
            );
            break;
        }
        case RETRIEVED_SCHEMA_ID:
        {
            SchemaRegistryRetrievableSchemaIdExFW ex = eventEx.retrievableSchemaId();
            result = String.format("Successfully fetched schema id %d.", ex.schemaId());
            break;
        }
        }
        return result;
    }

    private static String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
