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

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.ServerChannelFactory;

public class ZillaServerChannelFactory implements ServerChannelFactory
{
    private final ZillaServerChannelSink channelSink;
    private final ZillaEnginePool enginePool;

    public ZillaServerChannelFactory(
        ZillaEnginePool enginePool)
    {
        this.channelSink = new ZillaServerChannelSink();
        this.enginePool = enginePool;
    }

    @Override
    public ServerChannel newChannel(
        ChannelPipeline pipeline)
    {
        return new ZillaServerChannel(this, pipeline, channelSink, enginePool.nextEngine());
    }

    @Override
    public void shutdown()
    {
    }

    @Override
    public void releaseExternalResources()
    {
        shutdown();
    }
}
