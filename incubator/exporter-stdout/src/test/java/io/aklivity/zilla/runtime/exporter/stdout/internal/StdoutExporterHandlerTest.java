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
package io.aklivity.zilla.runtime.exporter.stdout.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.engine.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.config.StdoutExporterConfig;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;

public class StdoutExporterHandlerTest
{
    private static final Path ENGINE_PATH = Paths.get("target/zilla-itests");
    private static final Path EVENTS_PATH = ENGINE_PATH.resolve("events");
    private static final int TCP_TYPE_ID = 1;
    private static final String EXPECTED_OUTPUT = "ERROR: TCP Remote Access Failed [timestamp = 77] " +
        "[traceId = 0x0000000000000042] [binding = ns.binding] [address = address]\n";

    @Test
    @SuppressWarnings("unchecked")
    public void shouldStart()
    {
        // GIVEN
        EventsLayout eventsWriter = new EventsLayout.Builder()
            .path(EVENTS_PATH)
            .capacity(1024)
            .readonly(false)
            .build();
        MessageConsumer eventWriter = eventsWriter.supplyWriter();
        MutableDirectBuffer eventBuffer = new UnsafeBuffer(new byte[64]);
        TcpEventFW event = new TcpEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .remoteAccessFailed(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .address("address")
            ).build();
        eventWriter.accept(TCP_TYPE_ID, event.buffer(), 0, event.sizeof());
        EventsLayout eventReader = new EventsLayout.Builder()
            .path(EVENTS_PATH)
            .readonly(true)
            .build();
        eventReader.spyAt(RingBufferSpy.SpyPosition.ZERO);

        EngineConfiguration config = mock(EngineConfiguration.class);
        EngineContext context = mock(EngineContext.class);
        StdoutExporterConfig exporter = new StdoutExporterConfig(mock(ExporterConfig.class));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);
        when(context.lookupLabelId("tcp")).thenReturn(TCP_TYPE_ID);
        when(context.supplyNamespace(0x0000000200000007L)).thenReturn("ns");
        when(context.supplyLocalName(0x0000000200000007L)).thenReturn("binding");
        when(context.supplyEventSpies(RingBufferSpy.SpyPosition.ZERO)).thenReturn(
            new Supplier[] {eventReader::bufferSpy}
        );
        StdoutExporterHandler handler = new StdoutExporterHandler(config, context, exporter, ps);

        // WHEN
        handler.start();
        handler.export();

        // THEN
        assertThat(os.toString(), equalTo(EXPECTED_OUTPUT));
        handler.stop();
    }
}