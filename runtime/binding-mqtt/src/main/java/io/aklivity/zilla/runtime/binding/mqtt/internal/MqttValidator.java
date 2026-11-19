/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.mqtt.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.String16FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public final class MqttValidator
{
    private static final String SHARED_PREFIX = "$share/";
    private static final Pattern TOPIC_FILTER_REGEX =
        Pattern.compile("(?!$)[/]?(([^/#+\\u0000]*|\\+)/)*(#|\\+|[^/#+\\u0000]*)");
    private static final Pattern TOPIC_NAME_REGEX = Pattern.compile("[^#+\\u0000]+");

    private final Matcher topicFilterMatcher = TOPIC_FILTER_REGEX.matcher("");
    private final Matcher topicNameMatcher = TOPIC_NAME_REGEX.matcher("");

    public MqttValidator()
    {
    }

    public boolean isTopicFilterValid(
        String topicFilter)
    {
        return topicFilterMatcher.reset(topicFilter).matches();
    }

    public boolean isTopicNameValid(
        String topicName)
    {
        return topicNameMatcher.reset(topicName).matches();
    }

    public boolean isTopicNameValid(
        String16FW topicName)
    {
        final int length = topicName.length();
        final DirectBufferEx buffer = topicName.buffer();
        final int offset = topicName.offset() + Short.BYTES;

        boolean valid = length > 0;

        for (int index = 0; valid && index < length; index++)
        {
            final byte value = buffer.getByte(offset + index);
            valid = value != '#' && value != '+' && value != 0;
        }

        return valid;
    }

    public boolean isSharedTopicFilterValid(
        String topicFilter)
    {
        final int separator = shareSeparator(topicFilter);

        boolean valid = separator != -1 && separator > SHARED_PREFIX.length() && separator + 1 < topicFilter.length();

        for (int index = SHARED_PREFIX.length(); valid && index < separator; index++)
        {
            final char value = topicFilter.charAt(index);
            valid = value != '+' && value != '#';
        }

        return valid && topicFilterMatcher.reset(topicFilter).region(separator + 1, topicFilter.length()).matches();
    }

    public static boolean isShared(
        String topicFilter)
    {
        return topicFilter.startsWith(SHARED_PREFIX);
    }

    public static String shareName(
        String topicFilter)
    {
        final int separator = shareSeparator(topicFilter);

        return separator != -1 ? topicFilter.substring(SHARED_PREFIX.length(), separator) : null;
    }

    public static String sharePattern(
        String topicFilter)
    {
        final int separator = shareSeparator(topicFilter);

        return separator != -1 ? topicFilter.substring(separator + 1) : null;
    }

    public static boolean wildcardFirst(
        String topicFilter)
    {
        final int first = shareSeparator(topicFilter) + 1;

        return first < topicFilter.length() && (topicFilter.charAt(first) == '+' || topicFilter.charAt(first) == '#');
    }

    public static String topicFilter(
        String shareName,
        String pattern)
    {
        return shareName != null ? SHARED_PREFIX + shareName + "/" + pattern : pattern;
    }

    private static int shareSeparator(
        String topicFilter)
    {
        return isShared(topicFilter) ? topicFilter.indexOf('/', SHARED_PREFIX.length()) : -1;
    }
}
