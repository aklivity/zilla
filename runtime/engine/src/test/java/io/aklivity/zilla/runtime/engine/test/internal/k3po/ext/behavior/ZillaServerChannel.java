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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import static org.jboss.netty.channel.Channels.fireChannelOpen;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.AbstractServerChannel;

public final class ZillaServerChannel extends AbstractServerChannel<ZillaServerChannelConfig>
{
    final ZillaEngine engine;

    ZillaServerChannel(
        ChannelFactory factory,
        ChannelPipeline pipeline,
        ChannelSink sink,
        ZillaEngine engine)
    {
        super(factory, pipeline, sink, new DefaultZillaServerChannelConfig(), false);

        this.engine = engine;

        fireChannelOpen(this);
    }

    @Override
    public ZillaChannelAddress getLocalAddress()
    {
        return ZillaChannelAddress.class.cast(super.getLocalAddress());
    }

    @Override
    public ZillaChannelAddress getRemoteAddress()
    {
        return ZillaChannelAddress.class.cast(super.getRemoteAddress());
    }

    public int getLocalScope()
    {
        return Long.SIZE - 1;
    }

    protected void setLocalAddress(
        ZillaChannelAddress localAddress)
    {
        super.setLocalAddress(localAddress);
    }

    @Override
    protected void setBound()
    {
        super.setBound();
    }

    @Override
    protected void setTransport(Channel transport)
    {
        super.setTransport(transport);
    }

    @Override
    protected boolean setClosed()
    {
        return super.setClosed();
    }

    @Override
    protected Channel getTransport()
    {
        return super.getTransport();
    }
}
