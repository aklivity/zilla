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
package io.aklivity.zilla.runtime.cog.fan.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.Cog;

final class FanCog implements Cog
{
    static final String NAME = "fan";

    private final FanConfiguration config;

    FanCog(
        FanConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return FanCog.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/fan.json");
    }

    @Override
    public FanAxle supplyAxle(
        AxleContext context)
    {
        return new FanAxle(config, context);
    }
}
