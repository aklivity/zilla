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
package io.aklivity.zilla.runtime.binding.llm.internal.decode;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Dispatches to the {@link LlmContentDecoderSpi} registered for a stream's content-type.
 */
public final class LlmContentDecoderFactory
{
    private final Map<String, LlmContentDecoderSpi> decodersByContentType;

    public LlmContentDecoderFactory()
    {
        this.decodersByContentType = ServiceLoader
            .load(LlmContentDecoderSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(LlmContentDecoderSpi::contentType, identity()));
    }

    public Iterable<String> contentTypes()
    {
        return decodersByContentType.keySet();
    }

    public LlmContentDecoder create(
        String contentType)
    {
        LlmContentDecoderSpi decoderSpi = decodersByContentType.get(contentType);

        return decoderSpi != null ? decoderSpi.supply() : null;
    }
}
