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
package io.aklivity.zilla.runtime.binding.amqp.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class AmqpBinding implements Binding
{
    public static final String NAME = "amqp";

    private final AmqpConfiguration config;

    AmqpBinding(
        AmqpConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return AmqpBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/amqp.schema.patch.json");
    }

    @Override
    public AmqpBindingContext supply(
        EngineContext context)
    {
        return new AmqpBindingContext(config, context);
    }
}
