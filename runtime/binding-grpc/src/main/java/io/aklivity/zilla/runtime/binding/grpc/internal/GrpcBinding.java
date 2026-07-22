/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.grpc.internal;

import io.aklivity.zilla.config.binding.grpc.GrpcBindingInfo;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class GrpcBinding implements Binding
{
    public static final String NAME = GrpcBindingInfo.TYPE;

    private final GrpcConfiguration config;

    GrpcBinding(
        GrpcConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return GrpcBinding.NAME;
    }

    @Override
    public String originType(
        KindConfig kind)
    {
        return kind == KindConfig.CLIENT ? NAME : null;
    }

    @Override
    public String routedType(
        KindConfig kind)
    {
        return kind == KindConfig.SERVER ? NAME : null;
    }

    @Override
    public GrpcBindingContext supply(
        EngineContext context)
    {
        return new GrpcBindingContext(config, context);
    }
}

