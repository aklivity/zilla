/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.amqp.internal.stream;

import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.CLOSE_PIPE;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.CLOSE_RCVD;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.CLOSE_SENT;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.DISCARDING;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.END;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.ERROR;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.HDR_EXCH;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.HDR_RCVD;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.HDR_SENT;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.OC_PIPE;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.OPENED;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.OPEN_PIPE;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.OPEN_RCVD;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.OPEN_SENT;
import static io.aklivity.zilla.runtime.cog.amqp.internal.stream.AmqpConnectionState.START;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AmqpConnectionStateTest
{
    @Test
    public void shouldTransitionFromStart() throws Exception
    {
        assertEquals(HDR_SENT, START.sentHeader());
        assertEquals(HDR_RCVD, START.receivedHeader());
        assertEquals(ERROR, START.sentOpen());
        assertEquals(ERROR, START.receivedOpen());
        assertEquals(ERROR, START.sentClose());
        assertEquals(ERROR, START.receivedClose());
    }

    @Test
    public void shouldTransitionFromHeaderReceived() throws Exception
    {
        assertEquals(HDR_EXCH, HDR_RCVD.sentHeader());
        assertEquals(ERROR, HDR_RCVD.receivedHeader());
        assertEquals(ERROR, HDR_RCVD.sentOpen());
        assertEquals(ERROR, HDR_RCVD.receivedOpen());
        assertEquals(ERROR, HDR_RCVD.sentClose());
        assertEquals(ERROR, HDR_RCVD.receivedClose());
    }

    @Test
    public void shouldTransitionFromHeaderSent() throws Exception
    {
        assertEquals(ERROR, HDR_SENT.sentHeader());
        assertEquals(HDR_EXCH, HDR_SENT.receivedHeader());
        assertEquals(OPEN_PIPE, HDR_SENT.sentOpen());
        assertEquals(ERROR, HDR_SENT.receivedOpen());
        assertEquals(ERROR, HDR_SENT.sentClose());
        assertEquals(ERROR, HDR_SENT.receivedClose());
    }

    @Test
    public void shouldTransitionFromHeaderExchanged() throws Exception
    {
        assertEquals(ERROR, HDR_EXCH.sentHeader());
        assertEquals(ERROR, HDR_EXCH.receivedHeader());
        assertEquals(OPEN_SENT, HDR_EXCH.sentOpen());
        assertEquals(OPEN_RCVD, HDR_EXCH.receivedOpen());
        assertEquals(ERROR, HDR_EXCH.sentClose());
        assertEquals(ERROR, HDR_EXCH.receivedClose());
    }

    @Test
    public void shouldTransitionFromOpenReceived() throws Exception
    {
        assertEquals(ERROR, OPEN_RCVD.sentHeader());
        assertEquals(ERROR, OPEN_RCVD.receivedHeader());
        assertEquals(OPENED, OPEN_RCVD.sentOpen());
        assertEquals(ERROR, OPEN_RCVD.receivedOpen());
        assertEquals(ERROR, OPEN_RCVD.sentClose());
        assertEquals(ERROR, OPEN_RCVD.receivedClose());
    }

    @Test
    public void shouldTransitionFromOpenSent() throws Exception
    {
        assertEquals(ERROR, OPEN_SENT.sentHeader());
        assertEquals(ERROR, OPEN_SENT.receivedHeader());
        assertEquals(ERROR, OPEN_SENT.sentOpen());
        assertEquals(OPENED, OPEN_SENT.receivedOpen());
        assertEquals(CLOSE_PIPE, OPEN_SENT.sentClose());
        assertEquals(ERROR, OPEN_SENT.receivedClose());
    }

    @Test
    public void shouldTransitionFromOpened() throws Exception
    {
        assertEquals(ERROR, OPENED.sentHeader());
        assertEquals(ERROR, OPENED.receivedHeader());
        assertEquals(ERROR, OPENED.sentOpen());
        assertEquals(ERROR, OPENED.receivedOpen());
        assertEquals(DISCARDING, OPENED.sentClose());
        assertEquals(CLOSE_RCVD, OPENED.receivedClose());
    }

    @Test
    public void shouldTransitionFromCloseReceived() throws Exception
    {
        assertEquals(ERROR, CLOSE_RCVD.sentHeader());
        assertEquals(ERROR, CLOSE_RCVD.receivedHeader());
        assertEquals(ERROR, CLOSE_RCVD.sentOpen());
        assertEquals(ERROR, CLOSE_RCVD.receivedOpen());
        assertEquals(END, CLOSE_RCVD.sentClose());
        assertEquals(ERROR, CLOSE_RCVD.receivedClose());
    }

    @Test
    public void shouldTransitionFromCloseSent() throws Exception
    {
        assertEquals(ERROR, CLOSE_SENT.sentHeader());
        assertEquals(ERROR, CLOSE_SENT.receivedHeader());
        assertEquals(ERROR, CLOSE_SENT.sentOpen());
        assertEquals(ERROR, CLOSE_SENT.receivedOpen());
        assertEquals(ERROR, CLOSE_SENT.sentClose());
        assertEquals(END, CLOSE_SENT.receivedClose());
    }

    @Test
    public void shouldTransitionFromOpenPipelined() throws Exception
    {
        assertEquals(ERROR, OPEN_PIPE.sentHeader());
        assertEquals(OPEN_SENT, OPEN_PIPE.receivedHeader());
        assertEquals(ERROR, OPEN_PIPE.sentOpen());
        assertEquals(ERROR, OPEN_PIPE.receivedOpen());
        assertEquals(OC_PIPE, OPEN_PIPE.sentClose());
        assertEquals(ERROR, OPEN_PIPE.receivedClose());
    }

    @Test
    public void shouldTransitionFromOpenClosePipelined() throws Exception
    {
        assertEquals(ERROR, OC_PIPE.sentHeader());
        assertEquals(CLOSE_PIPE, OC_PIPE.receivedHeader());
        assertEquals(ERROR, OC_PIPE.sentOpen());
        assertEquals(ERROR, OC_PIPE.receivedOpen());
        assertEquals(ERROR, OC_PIPE.sentClose());
        assertEquals(ERROR, OC_PIPE.receivedClose());
    }

    @Test
    public void shouldTransitionFromClosePipelined() throws Exception
    {
        assertEquals(ERROR, CLOSE_PIPE.sentHeader());
        assertEquals(ERROR, CLOSE_PIPE.receivedHeader());
        assertEquals(ERROR, CLOSE_PIPE.sentOpen());
        assertEquals(CLOSE_SENT, CLOSE_PIPE.receivedOpen());
        assertEquals(ERROR, CLOSE_PIPE.sentClose());
        assertEquals(ERROR, CLOSE_PIPE.receivedClose());
    }
}
