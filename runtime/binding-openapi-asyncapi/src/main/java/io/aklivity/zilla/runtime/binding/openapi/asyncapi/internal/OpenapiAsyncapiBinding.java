/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class OpenapiAsyncapiBinding implements Binding
{
    public static final String NAME = "openapi-asyncapi";

    private final OpenapiAsyncapiConfiguration config;

    OpenapiAsyncapiBinding(
        OpenapiAsyncapiConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/openapi.asyncapi.schema.patch.json");
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
    public OpenapiAsyncapiBindingContext supply(
        EngineContext context)
    {
        return new OpenapiAsyncapiBindingContext(config, context);
    }
}
