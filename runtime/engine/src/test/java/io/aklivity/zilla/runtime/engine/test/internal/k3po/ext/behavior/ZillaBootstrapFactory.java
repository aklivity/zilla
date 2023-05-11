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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import org.jboss.netty.channel.ChannelFactory;
import org.kaazing.k3po.driver.internal.netty.bootstrap.BootstrapFactorySpi;
import org.kaazing.k3po.driver.internal.netty.bootstrap.ClientBootstrap;
import org.kaazing.k3po.driver.internal.netty.bootstrap.ServerBootstrap;

import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.ZillaExtConfiguration;

public class ZillaBootstrapFactory extends BootstrapFactorySpi
{
    private final ChannelFactory clientChannelFactory;
    private final ChannelFactory serverChannelFactory;
    private final ZillaEnginePool enginePool;

    public ZillaBootstrapFactory()
    {
        ZillaExtConfiguration config = new ZillaExtConfiguration();

        this.enginePool = new ZillaEnginePool(config);

        this.clientChannelFactory = new ZillaClientChannelFactory(enginePool);
        this.serverChannelFactory = new ZillaServerChannelFactory(enginePool);
    }

    @Override
    public String getTransportName()
    {
        return "zilla";
    }

    @Override
    public ClientBootstrap newClientBootstrap()
            throws Exception
    {
        return new ClientBootstrap(clientChannelFactory);
    }

    @Override
    public ServerBootstrap newServerBootstrap()
            throws Exception
    {
        return new ServerBootstrap(serverChannelFactory);
    }

    @Override
    public void shutdown()
    {
        enginePool.shutdown();
        clientChannelFactory.shutdown();
        serverChannelFactory.shutdown();
    }


    @Override
    public void releaseExternalResources()
    {
        shutdown();
        enginePool.releaseExternalResources();
        clientChannelFactory.releaseExternalResources();
        serverChannelFactory.releaseExternalResources();
    }
}
