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

import static org.jboss.netty.channel.Channels.fireChannelOpen;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.kaazing.k3po.driver.internal.netty.channel.ChannelAddress;

public final class ZillaClientChannel extends ZillaChannel
{
    private int remoteScope;

    ZillaClientChannel(
        ChannelFactory factory,
        ChannelPipeline pipeline,
        ChannelSink sink,
        ZillaEngine engine,
        long targetId)
    {
        super(null, factory, pipeline, sink, engine, targetId);

        fireChannelOpen(this);
    }

    @Override
    public long targetId()
    {
        return super.targetId() | ((long)(remoteScope & 0x7f) << 48);
    }

    public void setRemoteScope(
        int remoteScope)
    {
        this.remoteScope = remoteScope;
    }

    @Override
    public int getRemoteScope()
    {
        return remoteScope;
    }

    @Override
    protected void setLocalAddress(ChannelAddress localAddress)
    {
        super.setLocalAddress(localAddress);
    }

    @Override
    protected void setBound()
    {
        super.setBound();
    }

    @Override
    protected boolean setClosed()
    {
        return super.setClosed();
    }
}
