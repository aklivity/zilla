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

import static io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities.READ_EXTENSION;
import static io.aklivity.zilla.specs.binding.filesystem.internal.types.FileSystemCapabilities.READ_PAYLOAD;

import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.HttpBeginExFW;

public final class HttpFileSystemWithResolver
{
    private static final int HEADER_METHOD_MASK_HEAD = 1 << READ_EXTENSION.ordinal();
    private static final int HEADER_METHOD_MASK_GET = 1 << READ_PAYLOAD.ordinal() | 1 << READ_EXTENSION.ordinal();

    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");

    private static final String8FW HEADER_METHOD_NAME = new String8FW(":method");
    private static final String16FW HEADER_METHOD_VALUE_GET = new String16FW("GET");
    private static final String16FW HEADER_METHOD_VALUE_HEAD = new String16FW("HEAD");

    private final HttpFileSystemWithConfig with;
    private final Matcher paramsMatcher;

    private Function<MatchResult, String> replacer = r -> null;

    public HttpFileSystemWithResolver(
        HttpFileSystemWithConfig with)
    {
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
    }

    public void onConditionMatched(
        HttpFileSystemConditionMatcher condition)
    {
        this.replacer = r -> condition.parameter(r.group(1));
    }

    public HttpFileSystemWithResult resolve(
        HttpBeginExFW httpBeginEx)
    {
        // TODO: hoist to constructor if constant
        String path0 = with.path;
        Matcher pathMatcher = paramsMatcher.reset(with.path);
        if (pathMatcher.matches())
        {
            path0 = pathMatcher.replaceAll(replacer);
        }
        String16FW path = new String16FW(path0);

        HttpFileSystemWithResult result = null;

        HttpHeaderFW method = httpBeginEx.headers().matchFirst(h -> HEADER_METHOD_NAME.equals(h.name()));
        if (method != null)
        {
            if (HEADER_METHOD_VALUE_HEAD.equals(method.value()))
            {
                result = new HttpFileSystemWithResult(path, HEADER_METHOD_MASK_HEAD);
            }
            else if (HEADER_METHOD_VALUE_GET.equals(method.value()))
            {
                result = new HttpFileSystemWithResult(path, HEADER_METHOD_MASK_GET);
            }
        }
        return result;
    }
}
