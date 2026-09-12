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

/**
 * Service provider interface for a pluggable {@link LlmContentDecoder} implementation.
 * <p>
 * Each supported content-type provides an implementation, registered via {@link java.util.ServiceLoader}
 * in {@code META-INF/services/io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoderSpi}.
 * {@link LlmContentDecoderFactory} selects the correct provider by matching {@link #contentType()} against
 * the content-type of the stream being decoded.
 * </p>
 */
public interface LlmContentDecoderSpi
{
    /**
     * Returns the content-type this provider decodes, e.g. {@code "text/event-stream"}.
     *
     * @return the content-type
     */
    String contentType();

    /**
     * Creates a new {@link LlmContentDecoder} instance for a single stream.
     *
     * @return a new decoder
     */
    LlmContentDecoder supply();
}
