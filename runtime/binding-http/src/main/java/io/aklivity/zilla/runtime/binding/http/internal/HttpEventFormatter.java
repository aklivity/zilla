/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.StringFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpRequestAcceptedExFW;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public class HttpEventFormatter implements EventFormatterSpi
{
    private static final String REQUEST_ACCEPTED_FORMAT = "REQUEST_ACCEPTED %s %s %s %s %s";

    private final EventFW eventRO = new EventFW();
    private final HttpEventExFW httpEventExRO = new HttpEventExFW();

    @Override
    public String type()
    {
        return HttpBinding.NAME;
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final HttpEventExFW extension = httpEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case REQUEST_ACCEPTED:
        {
            HttpRequestAcceptedExFW ex = extension.requestAccepted();
            result = String.format(REQUEST_ACCEPTED_FORMAT, identity(ex.identity()), asString(ex.scheme()), asString(ex.method()),
                asString(ex.authority()), asString(ex.path()));
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

    private static String identity(
        StringFW identity)
    {
        int length = identity.length();
        return length <= 0 ? "-" : identity.asString();
    }
}
