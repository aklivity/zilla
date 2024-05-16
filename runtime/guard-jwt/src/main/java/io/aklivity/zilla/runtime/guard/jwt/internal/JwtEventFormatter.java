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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.StringFW;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.event.JwtAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.event.JwtEventExFW;

public final class JwtEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final JwtEventExFW jwtEventExRO = new JwtEventExFW();

    JwtEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final JwtEventExFW extension = jwtEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case AUTHORIZATION_FAILED:
        {
            JwtAuthorizationFailedExFW ex = extension.authorizationFailed();
            result = String.format("%s", identity(ex.identity()));
            break;
        }
        }
        return result;
    }

    private static String identity(
        StringFW identity)
    {
        int length = identity.length();
        return length <= 0 ? "-" : identity.asString();
    }
}
