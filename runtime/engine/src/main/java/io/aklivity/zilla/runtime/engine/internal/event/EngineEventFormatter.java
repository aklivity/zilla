/*
 * Copyright 2021-2026 Aklivity Inc.
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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineConfigAppliedExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineConfigRejectedExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineConfigWatcherFailedExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineEventExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineStartedExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineStoppedExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;

public final class EngineEventFormatter implements EventFormatterSpi
{
    private static final String CONFIG_WATCHER_FAILED_FORMAT =
        "Dynamic config reloading is disabled.";
    private static final String CONFIG_WATCHER_FAILED_WITH_REASON_FORMAT =
        CONFIG_WATCHER_FAILED_FORMAT + " %s.";
    private static final String CONFIG_APPLIED_FORMAT = "Config applied.";
    private static final String CONFIG_APPLIED_WITH_ETAG_FORMAT = "Config applied with etag %s.";
    private static final String CONFIG_REJECTED_FORMAT = "Config rejected. %s.";
    private static final String CONFIG_REJECTED_WITH_ETAG_FORMAT = "Config rejected with etag %s. %s.";

    private final EventFW eventRO = new EventFW();
    private final EngineEventExFW eventExRO = new EngineEventExFW();

    EngineEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
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
        case STARTED:
            EngineStartedExFW started = extension.started();
            text = started.message().asString();
            break;
        case STOPPED:
            EngineStoppedExFW stopped = extension.stopped();
            text = stopped.message().asString();
            break;
        case CONFIG_APPLIED:
            EngineConfigAppliedExFW configApplied = extension.configApplied();
            String appliedEtag = configApplied.etag().asString();
            text = appliedEtag != null
                ? String.format(CONFIG_APPLIED_WITH_ETAG_FORMAT, appliedEtag)
                : CONFIG_APPLIED_FORMAT;
            break;
        case CONFIG_REJECTED:
            EngineConfigRejectedExFW configRejected = extension.configRejected();
            String rejectedEtag = configRejected.etag().asString();
            String rejectedReason = configRejected.reason().asString();
            text = rejectedEtag != null
                ? String.format(CONFIG_REJECTED_WITH_ETAG_FORMAT, rejectedEtag, rejectedReason)
                : String.format(CONFIG_REJECTED_FORMAT, rejectedReason);
            break;
        }

        return text;
    }
}
