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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.config;

import java.util.Set;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSpecificationConfig;

public class OpenapiAsyncapiSpecConfig
{
    public final Set<OpenapiSpecificationConfig> openapi;
    public final Set<AsyncapiSpecificationConfig> asyncapi;

    public OpenapiAsyncapiSpecConfig(
        Set<OpenapiSpecificationConfig> openapi,
        Set<AsyncapiSpecificationConfig> asyncapi)
    {
        this.openapi = openapi;
        this.asyncapi = asyncapi;
    }
}
