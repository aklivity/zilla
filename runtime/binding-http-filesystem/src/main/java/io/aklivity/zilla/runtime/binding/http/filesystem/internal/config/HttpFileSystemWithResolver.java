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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.CREATE_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.CREATE_FILE;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.DELETE_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.DELETE_FILE;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.READ_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.READ_FILE;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.READ_METADATA;
import static io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities.WRITE_FILE;
import static java.util.stream.Collectors.toSet;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.aklivity.zilla.config.binding.http.filesystem.HttpFileSystemWithConfig;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;

public final class HttpFileSystemWithResolver
{
    private static final int HEADER_METHOD_MASK_HEAD = 1 << READ_METADATA.ordinal();
    private static final int HEADER_METHOD_MASK_GET = 1 << READ_FILE.ordinal() | 1 << READ_METADATA.ordinal();
    private static final int HEADER_METHOD_MASK_POST = 1 << CREATE_FILE.ordinal();
    private static final int HEADER_METHOD_MASK_PUT = 1 << WRITE_FILE.ordinal();
    private static final int HEADER_METHOD_MASK_DELETE = 1 << DELETE_FILE.ordinal();
    private static final int HEADER_METHOD_MASK_POST_DIRECTORY = 1 << CREATE_DIRECTORY.ordinal();
    private static final int HEADER_METHOD_MASK_DELETE_DIRECTORY = 1 << DELETE_DIRECTORY.ordinal();
    private static final int HEADER_METHOD_MASK_GET_DIRECTORY = 1 << READ_DIRECTORY.ordinal();

    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");
    private static final Pattern IDENTITY_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).identity\\}");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).attributes" +
            ".([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)\\}");
    private static final Pattern PREFER_WAIT_PATTERN = Pattern.compile("wait=(\\d+)");
    private static final String8FW HEADER_METHOD_NAME = new String8FW(":method");
    private static final String8FW HEADER_IF_NONE_MATCH_NAME = new String8FW("if-none-match");
    private static final String8FW HEADER_IF_MATCH_NAME = new String8FW("if-match");
    private static final String8FW HEADER_PREFER_NAME = new String8FW("prefer");
    private static final String16FW HEADER_METHOD_VALUE_GET = new String16FW("GET");
    private static final String16FW HEADER_METHOD_VALUE_HEAD = new String16FW("HEAD");
    private static final String16FW HEADER_METHOD_VALUE_POST = new String16FW("POST");
    private static final String16FW HEADER_METHOD_VALUE_PUT = new String16FW("PUT");
    private static final String16FW HEADER_METHOD_VALUE_DELETE = new String16FW("DELETE");

    public static Set<String> extractGuardNames(
        HttpFileSystemWithConfig with)
    {
        return Stream.of(with.path, with.directory)
            .filter(Objects::nonNull)
            .flatMap(v -> Stream.concat(
                IDENTITY_PATTERN.matcher(v).results(),
                ATTRIBUTE_PATTERN.matcher(v).results()))
            .map(r -> r.group(1))
            .collect(toSet());
    }

    private final String16FW etagRO = new String16FW();
    private final HttpFileSystemWithConfig with;
    private final Matcher paramsMatcher;
    private final Matcher identityMatcher;
    private final Matcher attributeMatcher;
    private final Matcher preferWaitMatcher;
    private final boolean pathResolvable;
    private final boolean pathMultiSegment;
    private final boolean directoryResolvable;
    private final boolean directoryMultiSegment;
    private final Function<MatchResult, String> identitySegmentReplacer;
    private final Function<MatchResult, String> attributeSegmentReplacer;
    private final Function<MatchResult, String> paramsSegmentReplacer;
    private final Function<MatchResult, String> paramsReplacer;

    private Function<MatchResult, String> replacer = r -> null;
    private long authorization;
    private boolean rejected;

    public HttpFileSystemWithResolver(
        LongObjectBiFunction<MatchResult, String> identityReplacer,
        LongObjectBiFunction<MatchResult, String> attributeReplacer,
        HttpFileSystemWithConfig with)
    {
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
        this.attributeMatcher = ATTRIBUTE_PATTERN.matcher("");
        this.preferWaitMatcher = PREFER_WAIT_PATTERN.matcher("");
        this.pathResolvable = resolvable(with.path);
        this.pathMultiSegment = multiSegment(with.path);
        this.directoryResolvable = resolvable(with.directory);
        this.directoryMultiSegment = multiSegment(with.directory);
        this.identitySegmentReplacer = r -> segment(identityReplacer.apply(authorization, r));
        this.attributeSegmentReplacer = r -> segment(attributeReplacer.apply(authorization, r));
        this.paramsSegmentReplacer = r -> segment(replacer.apply(r));
        this.paramsReplacer = r -> Matcher.quoteReplacement(replacer.apply(r));
    }

    public void onConditionMatched(
        HttpFileSystemConditionMatcher condition)
    {
        this.replacer = r ->
        {
            String replacement = condition.parameter(r.group(1));
            return replacement != null ? replacement : "";
        };
    }

    public HttpFileSystemWithResult resolve(
        long authorization,
        HttpBeginExFW httpBeginEx)
    {
        this.authorization = authorization;
        this.rejected = false;

        String path0 = with.path;
        if (pathResolvable)
        {
            path0 = resolve(path0, pathMultiSegment);
        }
        boolean isDir = path0 == null || path0.isEmpty() || path0.endsWith("/");
        String16FW path = new String16FW(path0);

        String directory0 = with.directory;
        if (directoryResolvable)
        {
            directory0 = resolve(directory0, directoryMultiSegment);
        }
        String16FW directory = new String16FW(directory0);

        String16FW etag = new String16FW("");

        HttpHeaderFW method = httpBeginEx.headers().matchFirst(h -> HEADER_METHOD_NAME.equals(h.name()));
        int capabilities = getCapabilities(method, isDir);
        HttpHeaderFW tag = httpBeginEx.headers().matchFirst(h ->
            HEADER_IF_MATCH_NAME.equals(h.name()) || HEADER_IF_NONE_MATCH_NAME.equals(h.name()));
        if (tag != null)
        {
            String16FW value = tag.value();
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
        return rejected
            ? null
            : new HttpFileSystemWithResult(directory, path, capabilities, etag, TimeUnit.SECONDS.toMillis(wait));
    }

    private String resolve(
        String value,
        boolean multiSegment)
    {
        String resolved = value;
        resolved = findAndReplace(resolved, identityMatcher, identitySegmentReplacer);
        resolved = findAndReplace(resolved, attributeMatcher, attributeSegmentReplacer);
        resolved = findAndReplace(resolved, paramsMatcher, multiSegment ? paramsReplacer : paramsSegmentReplacer);
        rejected |= !valid(resolved);
        return resolved;
    }

    private String segment(
        String value)
    {
        rejected |= value.indexOf('/') != -1;
        return Matcher.quoteReplacement(value);
    }

    private static String findAndReplace(
        String value,
        Matcher matcher,
        Function<MatchResult, String> replacer)
    {
        return matcher.reset(value).find()
            ? matcher.replaceAll(replacer)
            : value;
    }

    private static boolean resolvable(
        String value)
    {
        return value != null &&
            (PARAMS_PATTERN.matcher(value).find() ||
            IDENTITY_PATTERN.matcher(value).find() ||
            ATTRIBUTE_PATTERN.matcher(value).find());
    }

    private static boolean multiSegment(
        String value)
    {
        return value != null && PARAMS_PATTERN.matcher(value).matches();
    }

    private static boolean valid(
        String path)
    {
        final int length = path.length();
        final int limit = length > 0 && path.charAt(length - 1) == '/' ? length - 1 : length;

        boolean valid = limit > 0 && path.indexOf('\\') == -1;
        int segmentAt = 0;
        for (int index = 0; valid && index <= limit; index++)
        {
            if (index == limit || path.charAt(index) == '/')
            {
                final int segmentLength = index - segmentAt;
                valid = segmentLength != 0 &&
                    !(segmentLength == 1 && path.charAt(segmentAt) == '.') &&
                    !(segmentLength == 2 && path.charAt(segmentAt) == '.' && path.charAt(segmentAt + 1) == '.');
                segmentAt = index + 1;
            }
        }

        return valid;
    }

    private static int getCapabilities(
        HttpHeaderFW method,
        boolean isDir)
    {
        int capabilities = 0;
        if (method != null)
        {
            if (HEADER_METHOD_VALUE_HEAD.equals(method.value()))
            {
                capabilities = HEADER_METHOD_MASK_HEAD;
            }
            else if (HEADER_METHOD_VALUE_GET.equals(method.value()))
            {
                if (isDir)
                {
                    capabilities = HEADER_METHOD_MASK_GET_DIRECTORY;
                }
                else
                {
                    capabilities = HEADER_METHOD_MASK_GET;
                }
            }
            else if (HEADER_METHOD_VALUE_POST.equals(method.value()))
            {
                if (isDir)
                {
                    capabilities = HEADER_METHOD_MASK_POST_DIRECTORY;
                }
                else
                {
                    capabilities = HEADER_METHOD_MASK_POST;
                }
            }
            else if (HEADER_METHOD_VALUE_PUT.equals(method.value()))
            {
                capabilities = HEADER_METHOD_MASK_PUT;
            }
            else if (HEADER_METHOD_VALUE_DELETE.equals(method.value()))
            {
                if (isDir)
                {
                    capabilities = HEADER_METHOD_MASK_DELETE_DIRECTORY;
                }
                else
                {
                    capabilities = HEADER_METHOD_MASK_DELETE;
                }
            }
        }
        return capabilities;
    }
}
