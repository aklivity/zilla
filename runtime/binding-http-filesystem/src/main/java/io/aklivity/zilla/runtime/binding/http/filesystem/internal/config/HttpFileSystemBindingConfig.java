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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.Predicate;

import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class HttpFileSystemBindingConfig
{
    private static final Predicate<HttpHeaderFW> HEADER_NAME_PATH;

    static
    {
        String8FW headerPath = new String8FW(":path");
        HEADER_NAME_PATH = h -> headerPath.equals(h.name());
    }

    public final long id;
    public final String entry;
    public final KindConfig kind;
    public final List<HttpFileSystemRouteConfig> routes;

    public HttpFileSystemBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(HttpFileSystemRouteConfig::new).collect(toList());
    }

    public HttpFileSystemRouteConfig resolve(
        long authorization,
        HttpBeginExFW beginEx)
    {
        HttpHeaderFW pathHeader = beginEx != null ? beginEx.headers().matchFirst(HEADER_NAME_PATH) : null;
        String path = asPathWithoutQuery(pathHeader);
        return routes.stream()
                .filter(r -> r.authorized(authorization) && r.matches(path))
                .findFirst()
                .orElse(null);
    }

    private String asPathWithoutQuery(
        HttpHeaderFW pathHeader)
    {
        String path = null;

        if (pathHeader != null)
        {
            path = pathHeader.value().asString();

            int queryAt = path.indexOf('?');
            if (queryAt != -1)
            {
                path = path.substring(0, queryAt);
            }
        }

        return path;
    }
}
