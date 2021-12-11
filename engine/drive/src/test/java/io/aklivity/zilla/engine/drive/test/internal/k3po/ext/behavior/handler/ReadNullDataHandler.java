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
package io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.handler;

import static io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.NullChannelBuffer.NULL_BUFFER;

import java.util.EnumSet;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.kaazing.k3po.driver.internal.behavior.handler.event.AbstractEventHandler;

public class ReadNullDataHandler extends AbstractEventHandler
{
    public ReadNullDataHandler()
    {
        super(EnumSet.of(ChannelEventKind.MESSAGE));
    }

    @Override
    protected StringBuilder describe(
        StringBuilder sb)
    {
        return sb.append("read data.null");
    }

    @Override
    public void messageReceived(
        ChannelHandlerContext ctx,
        MessageEvent e) throws Exception
    {
        ChannelFuture handlerFuture = getHandlerFuture();

        try
        {
            if (!handlerFuture.isDone() &&
                e.getMessage() == NULL_BUFFER)
            {
                handlerFuture.setSuccess();
            }
            else
            {
                super.messageReceived(ctx, e);
            }
        }
        catch (Exception ex)
        {
            handlerFuture.setFailure(ex);
        }
    }
}
