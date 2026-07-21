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

import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;

public class InstanceId
{
    private final Supplier<String> supplyInstanceId;
    private volatile String16FW instanceId;

    InstanceId(
        Supplier<String> supplyInstanceId)
    {
        this.supplyInstanceId = supplyInstanceId;
        regenerate();
    }

    public void regenerate()
    {
        instanceId = new String16FW(supplyInstanceId.get());
    }

    public String16FW instanceId()
    {
        return instanceId;
    }
}
