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
package io.aklivity.zilla.runtime.binding.ws.internal.config;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class WsConditionConfig extends ConditionConfig
{
    public final String protocol;
    public final String scheme;
    public final String authority;
    public final String path;

    public WsConditionConfig(
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        this.protocol = protocol;
        this.scheme = scheme;
        this.authority = authority;
        this.path = path;
    }
}
