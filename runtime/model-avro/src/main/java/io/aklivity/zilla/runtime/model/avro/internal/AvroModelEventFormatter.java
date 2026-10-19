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
package io.aklivity.zilla.runtime.model.avro.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.model.avro.internal.types.event.AvroModelEventExFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.event.AvroModelParsingFailedExFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.event.AvroModelTransformFailedExFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.event.AvroModelValidationFailedExFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.event.EventFW;

public final class AvroModelEventFormatter implements EventFormatterSpi
{
    private static final String VALIDATION_FAILED_FORMAT = "A message payload failed validation.";
    private static final String VALIDATION_FAILED_WITH_ERROR_FORMAT = VALIDATION_FAILED_FORMAT + " %s";
    private static final String PARSING_FAILED_FORMAT = "A message payload could not be parsed.";
    private static final String PARSING_FAILED_WITH_ERROR_FORMAT = PARSING_FAILED_FORMAT + " %s";
    private static final String TRANSFORM_FAILED_FORMAT = "A message payload failed to transform.";
    private static final String TRANSFORM_FAILED_WITH_ERROR_FORMAT = TRANSFORM_FAILED_FORMAT + " %s";

    private final EventFW eventRO = new EventFW();
    private final AvroModelEventExFW eventExRO = new AvroModelEventExFW();

    AvroModelEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final AvroModelEventExFW eventEx = event.extension().get(eventExRO::wrap);

        String result = null;
        switch (eventEx.kind())
        {
        case VALIDATION_FAILED:
            result = formatValidationFailed(eventEx.validationFailed());
            break;
        case PARSING_FAILED:
            result = formatParsingFailed(eventEx.parsingFailed());
            break;
        case TRANSFORM_FAILED:
            result = formatTransformFailed(eventEx.transformFailed());
            break;
        }
        return result;
    }

    private String formatValidationFailed(
        AvroModelValidationFailedExFW validationFailed)
    {
        String error = validationFailed.error().asString();
        return error != null
            ? String.format(VALIDATION_FAILED_WITH_ERROR_FORMAT, error)
            : VALIDATION_FAILED_FORMAT;
    }

    private String formatParsingFailed(
        AvroModelParsingFailedExFW parsingFailed)
    {
        String error = parsingFailed.error().asString();
        return error != null
            ? String.format(PARSING_FAILED_WITH_ERROR_FORMAT, error)
            : PARSING_FAILED_FORMAT;
    }

    private String formatTransformFailed(
        AvroModelTransformFailedExFW transformFailed)
    {
        String error = transformFailed.error().asString();
        return error != null
            ? String.format(TRANSFORM_FAILED_WITH_ERROR_FORMAT, error)
            : TRANSFORM_FAILED_FORMAT;
    }
}
