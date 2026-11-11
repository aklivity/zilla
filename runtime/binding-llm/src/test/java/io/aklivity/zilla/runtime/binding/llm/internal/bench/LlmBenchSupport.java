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
package io.aklivity.zilla.runtime.binding.llm.internal.bench;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

final class LlmBenchSupport
{
    static final int STREAMING_FRAGMENT_LENGTH = 256;
    static final int SMALL_LENGTH = 400;
    static final int LARGE_LENGTH = 10_000;
    static final byte[] NO_BYTES = new byte[0];
    static final String ARGUMENTS_PREFIX = "{\\\"query\\\":\\\"";
    static final String ARGUMENTS_SUFFIX = "\\\"}";

    static int textLength(
        String size)
    {
        return "10k".equals(size) ? LARGE_LENGTH : SMALL_LENGTH;
    }

    static String base64(
        int length)
    {
        final byte[] random = new byte[length];
        new Random(0L).nextBytes(random);

        return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
    }

    static List<String> fragments(
        String text)
    {
        final List<String> fragments = new ArrayList<>();

        for (int offset = 0; offset < text.length(); offset += STREAMING_FRAGMENT_LENGTH)
        {
            fragments.add(text.substring(offset, Math.min(offset + STREAMING_FRAGMENT_LENGTH, text.length())));
        }

        return fragments;
    }

    static void add(
        List<UnsafeBufferEx> exs,
        List<UnsafeBufferEx> payloads,
        byte[] extension,
        String payload)
    {
        exs.add(new UnsafeBufferEx(extension));
        payloads.add(new UnsafeBufferEx(payload.getBytes(UTF_8)));
    }

    static byte[] controlEx(
        int llmTypeId)
    {
        return LlmFunctions.dataEx().typeId(llmTypeId).type("CONTROL").message(-1).build();
    }

    static byte[] userTextEx(
        int llmTypeId)
    {
        return LlmFunctions.dataEx().typeId(llmTypeId).type("USER_TEXT").message(0).build();
    }

    static byte[] toolCallEx(
        int llmTypeId)
    {
        return LlmFunctions.dataEx()
            .typeId(llmTypeId)
            .type("TOOL_CALL")
            .message(0)
            .attribute("tool-name", "lookup")
            .attribute("tool-call-id", "call_1")
            .build();
    }

    static byte[] httpDataEx(
        int httpTypeId,
        String type)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[128]);

        final HttpDataExFW httpDataEx = new HttpDataExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name("type").value(type))
            .build();

        final byte[] encoded = new byte[httpDataEx.sizeof()];
        buffer.getBytes(0, encoded);

        return encoded;
    }

    static byte[] httpResponseBeginEx(
        int httpTypeId,
        String contentType)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

        final HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":status").value("200"))
            .headersItem(h -> h.name("content-type").value(contentType))
            .build();

        final byte[] encoded = new byte[httpBeginEx.sizeof()];
        buffer.getBytes(0, encoded);

        return encoded;
    }

    private LlmBenchSupport()
    {
    }
}
