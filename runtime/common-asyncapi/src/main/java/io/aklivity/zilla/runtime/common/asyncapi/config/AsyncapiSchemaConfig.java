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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import java.util.Map;

import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;

public class AsyncapiSchemaConfig
{
    public final String specLabel;
    public final int schemaId;
    public final AsyncapiView asyncapi;
    public final Map<String, String> security;
    public final String store;

    public AsyncapiSchemaConfig(
        String specLabel,
        int schemaId,
        AsyncapiView asyncapi,
        Map<String, String> security,
        String store)
    {
        this.specLabel = specLabel;
        this.schemaId = schemaId;
        this.asyncapi = asyncapi;
        this.security = security;
        this.store = store;
    }

    public AsyncapiSchemaConfig(
        String specLabel,
        int schemaId,
        AsyncapiView asyncapi,
        Map<String, String> security)
    {
        this(specLabel, schemaId, asyncapi, security, null);
    }

    public AsyncapiSchemaConfig(
        String specLabel,
        int schemaId,
        AsyncapiView asyncapi)
    {
        this(specLabel, schemaId, asyncapi, null, null);
    }
}
