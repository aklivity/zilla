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
package io.aklivity.zilla.runtime.engine.internal.exporter;

import org.agrona.concurrent.Agent;

import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;

public class ExporterAgent implements Agent
{
    private final String agentName;
    private final ExporterHandler handler;

    public ExporterAgent(
        long exporterId,
        ExporterHandler handler)
    {
        this.agentName = String.format("engine/exporter#%d", exporterId);
        this.handler = handler;
    }

    @Override
    public void onStart()
    {
        handler.start();
    }

    @Override
    public int doWork()
    {
        return handler.export();
    }

    @Override
    public void onClose()
    {
        handler.stop();
    }

    @Override
    public String roleName()
    {
        return agentName;
    }
}
