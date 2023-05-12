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
package io.aklivity.zilla.runtime.binding.amqp.internal.stream;

import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.BEGIN_RCVD;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.BEGIN_SENT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.DISCARDING;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.END_RCVD;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.END_SENT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.ERROR;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.MAPPED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.UNMAPPED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AmqpSessionStateTest
{
    @Test
    public void shouldTransitionFromUnmapped() throws Exception
    {
        assertEquals(BEGIN_RCVD, UNMAPPED.receivedBegin());
        assertEquals(BEGIN_SENT, UNMAPPED.sentBegin());
        assertEquals(ERROR, UNMAPPED.receivedEnd());
        assertEquals(ERROR, UNMAPPED.sentEnd());
    }

    @Test
    public void shouldTransitionFromBeginSent() throws Exception
    {
        assertEquals(MAPPED, BEGIN_SENT.receivedBegin());
        assertEquals(ERROR, BEGIN_SENT.sentBegin());
        assertEquals(ERROR, BEGIN_SENT.receivedEnd());
        assertEquals(ERROR, BEGIN_SENT.sentEnd());
    }

    @Test
    public void shouldTransitionFromBeginReceived() throws Exception
    {
        assertEquals(ERROR, BEGIN_RCVD.receivedBegin());
        assertEquals(MAPPED, BEGIN_RCVD.sentBegin());
        assertEquals(ERROR, BEGIN_RCVD.receivedEnd());
        assertEquals(ERROR, BEGIN_RCVD.sentEnd());
    }

    @Test
    public void shouldTransitionFromMapped() throws Exception
    {
        assertEquals(ERROR, MAPPED.receivedBegin());
        assertEquals(ERROR, MAPPED.sentBegin());
        assertEquals(END_RCVD, MAPPED.receivedEnd());
        assertEquals(DISCARDING, MAPPED.sentEnd());
    }

    @Test
    public void shouldTransitionFromEndSent() throws Exception
    {
        assertEquals(ERROR, END_SENT.receivedBegin());
        assertEquals(ERROR, END_SENT.sentBegin());
        assertEquals(UNMAPPED, END_SENT.receivedEnd());
        assertEquals(ERROR, END_SENT.sentEnd());
    }

    @Test
    public void shouldTransitionFromEndReceived() throws Exception
    {
        assertEquals(ERROR, END_RCVD.receivedBegin());
        assertEquals(ERROR, END_RCVD.sentBegin());
        assertEquals(ERROR, END_RCVD.receivedEnd());
        assertEquals(UNMAPPED, END_RCVD.sentEnd());
    }

    @Test
    public void shouldTransitionFromDiscarding() throws Exception
    {
        assertEquals(ERROR, DISCARDING.receivedBegin());
        assertEquals(ERROR, DISCARDING.sentBegin());
        assertEquals(UNMAPPED, DISCARDING.receivedEnd());
        assertEquals(ERROR, DISCARDING.sentEnd());
    }
}
