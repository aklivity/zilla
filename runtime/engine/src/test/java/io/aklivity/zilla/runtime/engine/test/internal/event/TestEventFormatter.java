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
package io.aklivity.zilla.runtime.engine.test.internal.event;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.engine.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;

public final class TestEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final String8FW extensionRO = new String8FW();

    TestEventFormatter(
        Configuration config)
    {
    }

    @Override
    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        String8FW extension = extensionRO.wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        return extension.asString();
    }
}
