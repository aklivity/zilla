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
package io.aklivity.zilla.runtime.engine.internal.layouts;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.spy.RingBufferSpy;

public class EventsLayoutTest
{
    private static final Path EVENTS0_PATH = Paths.get("target/zilla-itests/events0");

    private int msgTypeId;

    @Test
    public void shouldWorkInGenericCase() throws Exception
    {
        // GIVEN
        EventsLayout eventsWriter = new EventsLayout.Builder()
            .path(EVENTS0_PATH)
            .capacity(1024)
            .readonly(false)
            .build();
        MessageConsumer eventWriter = eventsWriter.supplyWriter();
        eventWriter.accept(42, new UnsafeBuffer(), 0, 0);
        EventsLayout eventReader = new EventsLayout.Builder()
            .path(EVENTS0_PATH)
            .readonly(true)
            .build();
        eventReader.spyAt(RingBufferSpy.SpyPosition.ZERO);
        RingBufferSpy spy = eventReader.bufferSpy();
        msgTypeId = 0;

        // WHEN
        spy.spy(this::readEvent);

        // THEN
        assertThat(msgTypeId, equalTo(42));
    }

    private boolean readEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        this.msgTypeId = msgTypeId;
        return true;
    }
}
