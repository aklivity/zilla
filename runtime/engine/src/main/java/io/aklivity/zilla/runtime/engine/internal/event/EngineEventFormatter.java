/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.event;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineConfigWatcherFailedExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineEventExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;

public final class EngineEventFormatter implements EventFormatterSpi
{
    private static final String CONFIG_WATCHER_FAILED_FORMAT =
        "Dynamic config reloading is disabled.";
    private static final String CONFIG_WATCHER_FAILED_WITH_REASON_FORMAT =
        CONFIG_WATCHER_FAILED_FORMAT + " %s.";

    private final EventFW eventRO = new EventFW();
    private final EngineEventExFW eventExRO = new EngineEventExFW();

    EngineEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final EngineEventExFW extension = eventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());

        String text = null;
        switch (extension.kind())
        {
        case CONFIG_WATCHER_FAILED:
            EngineConfigWatcherFailedExFW configWatcherFailed = extension.configWatcherFailed();
            String reason = configWatcherFailed.reason().asString();
            String format = reason != null
                ? CONFIG_WATCHER_FAILED_WITH_REASON_FORMAT
                : CONFIG_WATCHER_FAILED_FORMAT;
            text = String.format(format, reason);
            break;
        }

        return text;
    }
}
