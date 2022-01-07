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
package io.aklivity.zilla.runtime.cog.ws.internal;

import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.Cog;

public final class WsCog implements Cog
{
    public static final String NAME = "ws";

    private final WsConfiguration config;

    WsCog(
        WsConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return WsCog.NAME;
    }

    @Override
    public WsAxle supplyAxle(
        AxleContext context)
    {
        return new WsAxle(config, context);
    }
}
