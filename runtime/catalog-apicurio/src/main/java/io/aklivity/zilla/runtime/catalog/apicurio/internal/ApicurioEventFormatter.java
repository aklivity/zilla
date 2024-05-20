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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.StringFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioRetrievableArtifactIdExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioRetrievableArtifactSubjectVersionExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioUnretrievableArtifactIdExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioUnretrievableArtifactSubjectVersionExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioUnretrievableArtifactSubjectVersionStaleArtifactExFW;
import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class ApicurioEventFormatter implements EventFormatterSpi
{
    private static final String UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION = "UNRETRIEVABLE_ARTIFACT %s %s";
    private static final String UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION_STALE_ARTIFACT =
        "UNRETRIEVABLE_ARTIFACT %s %s, USING_STALE_ARTIFACT %d";
    private static final String UNRETRIEVABLE_ARTIFACT_ID = "UNRETRIEVABLE_ARTIFACT_ID %d";
    private static final String RETRIEVED_ARTIFACT_SUBJECT_VERSION = "RETRIEVED_ARTIFACT_SUBJECT_VERSION %s %s";
    private static final String RETRIEVED_ARTIFACT_ID = "RETRIEVED_ARTIFACT_ID %d";

    private final EventFW eventRO = new EventFW();
    private final ApicurioEventExFW schemaRegistryEventExRO = new ApicurioEventExFW();

    ApicurioEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final ApicurioEventExFW extension = schemaRegistryEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION:
        {
            ApicurioUnretrievableArtifactSubjectVersionExFW ex = extension.unretrievableArtifactSubjectVersion();
            result = String.format(UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION, asString(ex.subject()), asString(ex.version()));
            break;
        }
        case UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION_STALE_ARTIFACT:
        {
            ApicurioUnretrievableArtifactSubjectVersionStaleArtifactExFW ex = extension
                .unretrievableArtifactSubjectVersionStaleArtifact();
            result = String.format(UNRETRIEVABLE_ARTIFACT_SUBJECT_VERSION_STALE_ARTIFACT, asString(ex.subject()),
                asString(ex.version()), ex.artifactId());
            break;
        }
        case UNRETRIEVABLE_ARTIFACT_ID:
        {
            ApicurioUnretrievableArtifactIdExFW ex = extension.unretrievableArtifactId();
            result = String.format(UNRETRIEVABLE_ARTIFACT_ID, ex.artifactId());
            break;
        }
        case RETRIEVED_ARTIFACT_SUBJECT_VERSION:
        {
            ApicurioRetrievableArtifactSubjectVersionExFW ex = extension.retrievableArtifactSubjectVersion();
            result = String.format(RETRIEVED_ARTIFACT_SUBJECT_VERSION, asString(ex.subject()), asString(ex.version()));
            break;
        }
        case RETRIEVED_ARTIFACT_ID:
        {
            ApicurioRetrievableArtifactIdExFW ex = extension.retrievableArtifactId();
            result = String.format(RETRIEVED_ARTIFACT_ID, ex.artifactId());
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
