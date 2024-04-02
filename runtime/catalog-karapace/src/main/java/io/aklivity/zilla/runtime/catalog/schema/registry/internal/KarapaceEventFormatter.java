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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.catalog.karapace.internal.types.StringFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventExFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceRemoteAccessRejectedExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class KarapaceEventFormatter implements EventFormatterSpi
{
    private static final String REMOTE_ACCESS_REJECTED = "REMOTE_ACCESS_REJECTED %s %s %d";

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
        case REMOTE_ACCESS_REJECTED:
        {
            KarapaceRemoteAccessRejectedExFW ex = extension.remoteAccessRejected();
            result = String.format(REMOTE_ACCESS_REJECTED, asString(ex.method()), asString(ex.url()),
                ex.status());
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
