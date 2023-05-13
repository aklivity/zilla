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

import static org.jboss.netty.util.internal.ExecutorUtil.shutdownNow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.ThreadRenamingRunnable;

import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.ZillaExtConfiguration;

public class ZillaEnginePool implements ExternalResourceReleasable
{
    private final ZillaExtConfiguration config;

    private ZillaEngine engine;
    private ExecutorService engineExecutor;

    public ZillaEnginePool(
        ZillaExtConfiguration config)
    {
        this.config = config;
    }

    public ZillaEngine nextEngine()
    {
        if (engine == null)
        {
            this.engine = new ZillaEngine(config);
            this.engineExecutor = Executors.newFixedThreadPool(1);
            this.engineExecutor.execute(new ThreadRenamingRunnable(engine, "Zilla Engine #1"));
        }

        return engine;
    }

    public void shutdown()
    {
        if (engine != null)
        {
            engine.shutdown();
        }
    }

    @Override
    public void releaseExternalResources()
    {
        shutdown();

        if (engine != null)
        {
            engine.releaseExternalResources();
            shutdownNow(engineExecutor);
        }
    }
}
