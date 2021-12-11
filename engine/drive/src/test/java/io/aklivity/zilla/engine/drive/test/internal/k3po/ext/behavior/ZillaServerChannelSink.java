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
package io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.AbstractServerChannelSink;

public class ZillaServerChannelSink extends AbstractServerChannelSink<ZillaServerChannel>
{
    @Override
    protected void bindRequested(
        ChannelPipeline pipeline,
        ChannelStateEvent evt) throws Exception
    {
        ZillaServerChannel serverChannel = (ZillaServerChannel) evt.getChannel();
        ZillaChannelAddress localAddress = (ZillaChannelAddress) evt.getValue();
        ChannelFuture bindFuture = evt.getFuture();

        serverChannel.engine.bind(serverChannel, localAddress, bindFuture);
    }

    @Override
    protected void unbindRequested(
        ChannelPipeline pipeline,
        ChannelStateEvent evt)
            throws Exception
    {
        final ZillaServerChannel serverChannel = (ZillaServerChannel) evt.getChannel();
        final ChannelFuture unbindFuture = evt.getFuture();

        serverChannel.engine.unbind(serverChannel, unbindFuture);
    }

    @Override
    protected void closeRequested(
        ChannelPipeline pipeline,
        ChannelStateEvent evt)
            throws Exception
    {
        final ZillaServerChannel serverChannel = (ZillaServerChannel) evt.getChannel();

        serverChannel.engine.close(serverChannel);
    }

}
