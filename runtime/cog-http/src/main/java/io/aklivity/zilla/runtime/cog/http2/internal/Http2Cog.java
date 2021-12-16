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
package io.aklivity.zilla.runtime.cog.http2.internal;

import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.Cog;

public final class Http2Cog implements Cog
{
    public static final String NAME = "http2";

    private final Http2Configuration config;

    Http2Cog(
        Http2Configuration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return Http2Cog.NAME;
    }

    @Override
    public Http2Configuration config()
    {
        return config;
    }

    @Override
    public Http2Axle supplyAxle(
        AxleContext context)
    {
        return new Http2Axle(config, context);
    }
}
