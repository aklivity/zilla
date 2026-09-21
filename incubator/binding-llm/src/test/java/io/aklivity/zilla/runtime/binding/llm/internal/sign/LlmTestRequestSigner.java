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
package io.aklivity.zilla.runtime.binding.llm.internal.sign;

import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;

public final class LlmTestRequestSigner implements LlmRequestSigner
{
    private static final String HEADER_NAME = "x-test-signature";

    @Override
    public List<Map.Entry<String, String>> sign(
        String method,
        String scheme,
        String authority,
        String path,
        List<Map.Entry<String, String>> headers,
        DirectBuffer body,
        int bodyOffset,
        int bodyLength)
    {
        final String value = String.format("%s:%s:%d", method, path, bodyLength);
        return List.of(Map.entry(HEADER_NAME, value));
    }
}
