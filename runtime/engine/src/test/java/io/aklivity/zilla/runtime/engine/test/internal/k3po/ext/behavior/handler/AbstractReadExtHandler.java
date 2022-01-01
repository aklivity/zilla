/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.handler;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.Set;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.ChannelDecoder;
import org.kaazing.k3po.driver.internal.behavior.handler.event.AbstractEventHandler;

public abstract class AbstractReadExtHandler extends AbstractEventHandler
{
    protected final ChannelDecoder decoder;

    protected AbstractReadExtHandler(
        Set<ChannelEventKind> interestedEvents,
        Set<ChannelEventKind> expectedEvents,
        ChannelDecoder decoder)
    {
        super(interestedEvents, expectedEvents);
        this.decoder = requireNonNull(decoder, "decoder");
    }

    protected AbstractReadExtHandler(
        Set<ChannelEventKind> expectedEvents,
        ChannelDecoder decoder)
    {
        super(expectedEvents);
        this.decoder = requireNonNull(decoder, "decoder");
    }

    protected final void doReadExtension(
        ChannelHandlerContext ctx)
    {
        Channel channel = ctx.getChannel();
        ChannelFuture handlerFuture = getHandlerFuture();

        try
        {
            if (!handlerFuture.isDone() &&
                decoder.decode(channel))
            {
                handlerFuture.setSuccess();
            }
        }
        catch (Exception e)
        {
            handlerFuture.setFailure(e);
        }
    }

    @Override
    protected StringBuilder describe(StringBuilder sb)
    {
        return sb.append(format("read %s", decoder));
    }
}
