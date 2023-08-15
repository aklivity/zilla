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
package io.aklivity.zilla.runtime.command.config.internal.asyncapi.http.proxy;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.command.config.internal.airline.ConfigGenerator;
import io.aklivity.zilla.runtime.command.config.internal.asyncapi.model.AsyncApi;

public class AsyncApiHttpProxyConfigGenerator implements ConfigGenerator
{
    private final InputStream inputStream;

    private AsyncApi asyncApi;

    public AsyncApiHttpProxyConfigGenerator(
        InputStream inputStream)
    {
        this.inputStream = inputStream;

    }

    private AsyncApi parseAsyncApi(
        InputStream inputStream)
    {
        AsyncApi asyncApi = null;
        try
        {
            Jsonb jsonb = JsonbBuilder.create();
            asyncApi = jsonb.fromJson(inputStream, AsyncApi.class);
            jsonb.close();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return asyncApi;
    }

    @Override
    public String generate()
    {
        this.asyncApi = parseAsyncApi(inputStream);
        return "hello";
    }
}
