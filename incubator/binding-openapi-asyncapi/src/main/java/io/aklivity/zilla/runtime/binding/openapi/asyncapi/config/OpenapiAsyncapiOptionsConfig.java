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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.config;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class OpenapiAsyncapiOptionsConfig extends OptionsConfig
{
    public final OpenapiAsyncapiSpecConfig specs;

    public OpenapiAsyncapiOptionsConfig(
        OpenapiAsyncapiSpecConfig specs)
    {
        this.specs = specs;
    }

    public long resolveOpenapiApiId(
        String apiLabel)
    {
        long apiId = -1;
        for (OpenapiConfig c : specs.openapi)
        {
            if (c.apiLabel.equals(apiLabel))
            {
                apiId = c.apiId;
                break;
            }
        }
        return apiId;
    }

    public long resolveAsyncapiApiId(
        String apiLabel)
    {
        long apiId = -1;
        for (AsyncapiConfig c : specs.asyncapi)
        {
            if (c.apiLabel.equals(apiLabel))
            {
                apiId = c.apiId;
                break;
            }
        }
        return apiId;
    }
}
