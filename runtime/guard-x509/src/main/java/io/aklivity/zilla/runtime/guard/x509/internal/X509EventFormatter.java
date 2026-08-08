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
package io.aklivity.zilla.runtime.guard.x509.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.guard.x509.internal.types.StringFW;
import io.aklivity.zilla.runtime.guard.x509.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.guard.x509.internal.types.event.X509AuthorizationFailedExFW;
import io.aklivity.zilla.runtime.guard.x509.internal.types.event.X509EventExFW;

public final class X509EventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final X509EventExFW x509EventExRO = new X509EventExFW();

    X509EventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final X509EventExFW extension = x509EventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case AUTHORIZATION_FAILED:
        {
            X509AuthorizationFailedExFW ex = extension.authorizationFailed();
            result = String.format("X509 certificate chain authorization failed for identity (%s). %s",
                    asString(ex.identity()),
                    asString(ex.reason())
            );
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
