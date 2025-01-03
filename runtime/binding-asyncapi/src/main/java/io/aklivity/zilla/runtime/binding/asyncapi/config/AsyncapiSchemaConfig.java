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
package io.aklivity.zilla.runtime.binding.asyncapi.config;


import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiView;

public class AsyncapiSchemaConfig
{
    public final String apiLabel;
    public final int schemaId;
    public final AsyncapiView asyncapi;

    public AsyncapiSchemaConfig(
        String apiLabel,
        int schemaId,
        AsyncapiView asyncapi)
    {
        this.apiLabel = apiLabel;
        this.schemaId = schemaId;
        this.asyncapi = asyncapi;
    }
}
