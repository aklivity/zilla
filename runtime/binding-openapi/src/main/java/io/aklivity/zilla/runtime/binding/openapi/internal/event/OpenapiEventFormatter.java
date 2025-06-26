/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.internal.event;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.openapi.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.event.OpenapiEventExFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.event.OpenapiUnresolvedRefExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class OpenapiEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final OpenapiEventExFW asyncapiEventExRO = new OpenapiEventExFW();

    OpenapiEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final OpenapiEventExFW extension = asyncapiEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case UNRESOLVED_REF:
        {
            OpenapiUnresolvedRefExFW ex = extension.unresolvedRef();
            result = String.format("Unresolved reference (%s).", asString(ex.ref()));
            break;
        }
        }
        return result;
    }

    private static String asString(
        String16FW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
