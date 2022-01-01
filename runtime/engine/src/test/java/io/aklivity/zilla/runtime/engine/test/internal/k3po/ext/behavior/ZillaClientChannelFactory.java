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

import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;

public class ZillaClientChannelFactory implements ChannelFactory
{
    private final ZillaClientChannelSink channelSink;
    private final ZillaEnginePool enginePool;
    private final AtomicLong initialId;

    public ZillaClientChannelFactory(
        ZillaEnginePool enginePool)
    {
        this.channelSink = new ZillaClientChannelSink();
        this.enginePool = enginePool;
        this.initialId = new AtomicLong(((long)(Long.SIZE - 1) << 56) | 0x0000_0000_0000_0001L);
    }

    @Override
    public Channel newChannel(
        ChannelPipeline pipeline)
    {
        final long targetId = initialId.addAndGet(2L);
        return new ZillaClientChannel(this, pipeline, channelSink, enginePool.nextEngine(), targetId);
    }

    @Override
    public void shutdown()
    {
    }

    @Override
    public void releaseExternalResources()
    {
    }
}
