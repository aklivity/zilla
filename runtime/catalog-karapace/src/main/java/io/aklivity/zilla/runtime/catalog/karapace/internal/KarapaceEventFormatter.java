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
package io.aklivity.zilla.runtime.catalog.karapace.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.catalog.karapace.internal.types.StringFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventExFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceRetrievedSchemaIdExFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceRetrievedSchemaSubjectVersionExFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceUnretrievableSchemaIdExFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceUnretrievableSchemaSubjectVersionExFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceUnretrievableSchemaSubjectVersionStaleSchemaExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class KarapaceEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final KarapaceEventExFW karapaceEventExRO = new KarapaceEventExFW();

    KarapaceEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final KarapaceEventExFW extension = karapaceEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION:
        {
            KarapaceUnretrievableSchemaSubjectVersionExFW ex = extension.unretrievableSchemaSubjectVersion();
            result = String.format(
                    "Unable to fetch schema for subject %s with version %s.",
                    asString(ex.subject()),
                    asString(ex.version())
            );
            break;
        }
        case UNRETRIEVABLE_SCHEMA_SUBJECT_VERSION_STALE_SCHEMA:
        {
            KarapaceUnretrievableSchemaSubjectVersionStaleSchemaExFW ex = extension
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
            KarapaceUnretrievableSchemaIdExFW ex = extension.unretrievableSchemaId();
            result = String.format("Unable to fetch schema id %d.", ex.schemaId());
            break;
        }
        case RETRIEVED_SCHEMA_SUBJECT_VERSION:
        {
            KarapaceRetrievedSchemaSubjectVersionExFW ex = extension.retrievedSchemaSubjectVersion();
            result = String.format("Successfully fetched schema for subject %s with version %s.",
                    asString(ex.subject()),
                    asString(ex.version())
            );
            break;
        }
        case RETRIEVED_SCHEMA_ID:
        {
            KarapaceRetrievedSchemaIdExFW ex = extension.retrievedSchemaId();
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
