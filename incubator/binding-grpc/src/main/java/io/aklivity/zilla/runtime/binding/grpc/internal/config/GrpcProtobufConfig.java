/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;


import java.util.Set;

import org.agrona.collections.ObjectHashSet;

public class GrpcProtobufConfig
{
    public String location;
    public Set<GrpcServiceConfig> serviceConfigs;

    public GrpcProtobufConfig(
        String location)
    {
        this.location = location;
        this.serviceConfigs = new ObjectHashSet<>();
    }

    public void setServiceConfig(
        GrpcServiceConfig service)
    {
        serviceConfigs.add(service);
    }
}
