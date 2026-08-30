/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.http;

import static java.util.function.Function.identity;

import java.util.List;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;

public class HttpResponseConfig extends Config
{
    public final List<String> status;
    public final List<String> contentType;
    public final List<HttpParamConfig> headers;
    public final ModelConfig content;
    private final List<NamedConfig> refs;

    HttpResponseConfig(
        List<String> status,
        List<String> contentType,
        List<HttpParamConfig> headers,
        ModelConfig content,
        List<NamedConfig> refs)
    {
        this.status = status;
        this.contentType = contentType;
        this.headers = headers;
        this.content = content;
        this.refs = refs;
    }

    public List<NamedConfig> refs()
    {
        return refs;
    }

    public static HttpResponseConfigBuilder<HttpResponseConfig> builder()
    {
        return new HttpResponseConfigBuilder<>(identity());
    }
}
