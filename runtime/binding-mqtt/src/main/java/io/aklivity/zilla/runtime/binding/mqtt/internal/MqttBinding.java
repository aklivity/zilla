/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

import io.aklivity.zilla.config.binding.mqtt.MqttBindingInfo;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class MqttBinding implements Binding
{
    public static final String NAME = MqttBindingInfo.TYPE;

    private final MqttConfiguration config;
    private final InstanceId instanceId;


    MqttBinding(
        MqttConfiguration config)
    {
        this.config = config;
        this.instanceId = new InstanceId(config.instanceId());
    }

    @Override
    public String name()
    {
        return MqttBinding.NAME;
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
    public MqttBindingContext supply(
        EngineContext context)
    {
        return new MqttBindingContext(config, context, instanceId);
    }
}
