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
package io.aklivity.zilla.runtime.binding.llm.internal.encode;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Dispatches to the {@link LlmContentEncoderSpi} registered for a stream's content-type.
 */
public final class LlmContentEncoderFactory
{
    private final Map<String, LlmContentEncoderSpi> encodersByContentType;

    public LlmContentEncoderFactory()
    {
        this.encodersByContentType = ServiceLoader
            .load(LlmContentEncoderSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(LlmContentEncoderSpi::contentType, identity()));
    }

    public Iterable<String> contentTypes()
    {
        return encodersByContentType.keySet();
    }

    public LlmContentEncoder create(
        String contentType)
    {
        LlmContentEncoderSpi encoderSpi = encodersByContentType.get(contentType);

        return encoderSpi != null ? encoderSpi.supply() : null;
    }
}
