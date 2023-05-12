/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class HttpKafkaBinding implements Binding
{
    public static final String NAME = "http-kafka";

    private final HttpKafkaConfiguration config;

    HttpKafkaBinding(
        HttpKafkaConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return HttpKafkaBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/http.kafka.schema.patch.json");
    }

    @Override
    public HttpKafkaBindingContext supply(
        EngineContext context)
    {
        return new HttpKafkaBindingContext(config, context);
    }
}
