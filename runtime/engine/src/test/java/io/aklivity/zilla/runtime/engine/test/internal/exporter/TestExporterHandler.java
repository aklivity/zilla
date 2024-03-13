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
package io.aklivity.zilla.runtime.engine.test.internal.exporter;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.test.internal.exporter.config.TestExporterOptionsConfig;

class TestExporterHandler implements ExporterHandler
{
    private final EngineContext context;
    private final TestExporterOptionsConfig options;
    private final MessageReader readEvent;
    private final EventFormatter formatter;
    private final EventFW eventRO = new EventFW();

    private int eventIndex;

    TestExporterHandler(
        EngineContext context,
        ExporterConfig exporter)
    {
        this.context = context;
        this.readEvent = context.supplyEventReader();
        this.formatter = context.supplyEventFormatter();
        this.options = (TestExporterOptionsConfig) exporter.options;
    }

    @Override
    public void start()
    {
    }

    @Override
    public int export()
    {
        return readEvent.read(this::handleEvent, 1);
    }

    @Override
    public void stop()
    {
    }

    private void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        String qname = context.supplyQName(event.namespacedId());
        String id = context.supplyLocalName(event.id());
        String message = formatter.format(msgTypeId, buffer, index, length);
        if (options.events != null && eventIndex < options.events.size())
        {
            TestExporterOptionsConfig.Event e = options.events.get(eventIndex);
            if (!qname.equals(e.qName) || !id.equals(e.id) || !message.equals(e.message))
            {
                throw new IllegalStateException(String.format("event mismatch, expected: %s %s %s, got: %s %s %s",
                    e.qName, e.id, e.message, qname, id, message));
            }
            eventIndex++;
        }
    }
}
