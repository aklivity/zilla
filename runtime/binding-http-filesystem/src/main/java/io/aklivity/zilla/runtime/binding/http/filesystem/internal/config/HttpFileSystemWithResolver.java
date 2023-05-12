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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.READ_EXTENSION;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.READ_PAYLOAD;

import java.util.concurrent.TimeUnit;
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
    public static final int HEADER_METHOD_MASK_HEAD = 1 << READ_EXTENSION.ordinal();
    private static final int HEADER_METHOD_MASK_GET = 1 << READ_PAYLOAD.ordinal() | 1 << READ_EXTENSION.ordinal();

    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");
    private static final Pattern PREFER_WAIT_PATTERN = Pattern.compile("wait=(\\d+)");
    private static final String8FW HEADER_METHOD_NAME = new String8FW(":method");
    private static final String8FW HEADER_IF_NONE_MATCH_NAME = new String8FW("if-none-match");
    private static final String8FW HEADER_PREFER_NAME = new String8FW("prefer");
    private static final String16FW HEADER_METHOD_VALUE_GET = new String16FW("GET");
    private static final String16FW HEADER_METHOD_VALUE_HEAD = new String16FW("HEAD");

    private final String16FW etagRO = new String16FW();
    private final HttpFileSystemWithConfig with;
    private final Matcher paramsMatcher;
    private final Matcher preferWaitMatcher;

    private Function<MatchResult, String> replacer = r -> null;

    public HttpFileSystemWithResolver(
        HttpFileSystemWithConfig with)
    {
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.preferWaitMatcher = PREFER_WAIT_PATTERN.matcher("");
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

        HttpHeaderFW method = httpBeginEx.headers().matchFirst(h -> HEADER_METHOD_NAME.equals(h.name()));
        int capabilities = 0;
        if (method != null)
        {
            if (HEADER_METHOD_VALUE_HEAD.equals(method.value()))
            {
                capabilities = HEADER_METHOD_MASK_HEAD;
            }
            else if (HEADER_METHOD_VALUE_GET.equals(method.value()))
            {
                capabilities = HEADER_METHOD_MASK_GET;
            }
        }
        HttpHeaderFW ifNotMatched = httpBeginEx.headers().matchFirst(h -> HEADER_IF_NONE_MATCH_NAME.equals(h.name()));
        String16FW etag = new String16FW("");
        if (ifNotMatched != null)
        {
            String16FW value = ifNotMatched.value();
            etag = etagRO.wrap(value.buffer(), value.offset(), value.limit());
        }
        HttpHeaderFW prefer = httpBeginEx.headers().matchFirst(h -> HEADER_PREFER_NAME.equals(h.name()));
        int wait = 0;
        if (prefer != null)
        {
            Matcher waitMatcher = preferWaitMatcher.reset(prefer.value().asString());
            if (waitMatcher.find())
            {
                wait = Integer.parseInt(waitMatcher.group(1));
            }
        }
        return new HttpFileSystemWithResult(path, capabilities, etag, TimeUnit.SECONDS.toMillis(wait));
    }
}
