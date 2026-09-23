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
package io.aklivity.zilla.runtime.binding.llm.internal.codec;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentCodecSpi;
import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentEncoder;

/**
 * Dispatches to the {@link LlmContentCodecSpi} registered for a stream's content-type.
 */
public final class LlmContentCodecFactory
{
    private final Map<String, LlmContentCodecSpi> codecsByContentType;

    public LlmContentCodecFactory()
    {
        this.codecsByContentType = ServiceLoader
            .load(LlmContentCodecSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(LlmContentCodecSpi::contentType, identity()));
    }

    public Iterable<String> contentTypes()
    {
        return codecsByContentType.keySet();
    }

    public LlmContentDecoder createDecoder(
        String contentType)
    {
        LlmContentCodecSpi codecSpi = codecsByContentType.get(mediaType(contentType));

        return codecSpi != null ? codecSpi.supplyDecoder() : null;
    }

    public LlmContentEncoder createEncoder(
        String contentType)
    {
        LlmContentCodecSpi codecSpi = codecsByContentType.get(mediaType(contentType));

        return codecSpi != null ? codecSpi.supplyEncoder() : null;
    }

    // a content-type header is a media type optionally followed by ";"-separated
    // parameters (e.g. "application/json; charset=utf-8"); codecs are registered
    // by media type alone, so any parameters are stripped before lookup
    private static String mediaType(
        String contentType)
    {
        String mediaType = contentType;

        if (mediaType != null)
        {
            final int separator = mediaType.indexOf(';');
            mediaType = (separator != -1 ? mediaType.substring(0, separator) : mediaType).trim();
        }

        return mediaType;
    }
}
