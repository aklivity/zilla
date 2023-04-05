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
package io.aklivity.zilla.runtime.binding.tls.internal;

import static io.aklivity.zilla.runtime.engine.config.StreamType.PROXY;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.StreamType;

public final class TlsBinding implements Binding
{
    public static final String NAME = "tls";

    private final TlsConfiguration config;

    TlsBinding(
        TlsConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return TlsBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/tls.schema.patch.json");
    }

    @Override
    public TlsBindingContext supply(
        EngineContext context)
    {
        return new TlsBindingContext(config, context);
    }

    @Override
    public StreamType originType(
        KindConfig kind)
    {
        StreamType result;
        switch (kind)
        {
        case SERVER:
        case CLIENT:
            result = PROXY;
            break;
        default:
            result = null;
            break;
        }
        return result;
    }

    @Override
    public StreamType routedType(
        KindConfig kind)
    {
        StreamType result;
        switch (kind)
        {
        case SERVER:
        case CLIENT:
            result = PROXY;
            break;
        default:
            result = null;
            break;
        }
        return result;
    }
}
