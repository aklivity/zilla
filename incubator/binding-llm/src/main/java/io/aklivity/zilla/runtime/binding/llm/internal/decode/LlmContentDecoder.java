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

import org.agrona.DirectBuffer;

/**
 * Decodes a single stream's content-type framing, reporting decoded event frames to an
 * {@link LlmContentDecoderOutput} as progress is made.
 * <p>
 * An instance is confined to one stream; {@link LlmContentDecoderSpi#supply()} creates a fresh
 * instance per stream so decode state is never shared.
 * </p>
 */
@FunctionalInterface
public interface LlmContentDecoder
{
    /**
     * Decodes as much of {@code buffer[offset, limit)} as currently possible, reporting decoded
     * event frames to {@code output} as they are recognized.
     *
     * @param buffer  the buffer holding the undecoded bytes
     * @param offset  the offset of the undecoded bytes within {@code buffer}
     * @param limit   the limit of the undecoded bytes within {@code buffer}
     * @param output  receives the decoded event frames
     * @return the new offset; equal to {@code offset} when no further progress is currently possible
     */
    int decode(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output);
}
