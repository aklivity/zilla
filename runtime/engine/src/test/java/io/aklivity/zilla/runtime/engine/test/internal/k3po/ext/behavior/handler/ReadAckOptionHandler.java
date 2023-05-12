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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.handler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.kaazing.k3po.driver.internal.behavior.handler.command.AbstractCommandHandler;

import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaChannel;

public class ReadAckOptionHandler extends AbstractCommandHandler
{
    private int ack;

    public ReadAckOptionHandler(
        int ack)
    {
        this.ack = ack;
    }

    @Override
    protected void invokeCommand(
        ChannelHandlerContext ctx) throws Exception
    {
        ZillaChannel channel = (ZillaChannel) ctx.getChannel();
        channel.sourceAck(ack);
        ChannelFuture handlerFuture = getHandlerFuture();
        Channels.setInterestOps(ctx, handlerFuture, Channel.OP_READ);
    }
}
