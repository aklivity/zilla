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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

/**
 * Scopes the circuit of an exit stream to the model of the request.
 * <p>
 * The circuit identifier is the 64-bit FNV-1a hash of the UTF-8 bytes of the model, computed without allocation,
 * so every worker derives the same identifier for the same model. A hash of zero is remapped to one because zero
 * selects the default circuit of the binding.
 */
final class LlmCircuitId
{
    private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;
    private static final long DEFAULT_CIRCUIT_ID = 0L;

    static long of(
        String model)
    {
        long circuitId = DEFAULT_CIRCUIT_ID;

        if (model != null)
        {
            long hash = OFFSET_BASIS;

            for (int index = 0, length = model.length(); index < length; index++)
            {
                final char ch = model.charAt(index);

                if (ch < 0x80)
                {
                    hash = mix(hash, ch);
                }
                else if (ch < 0x800)
                {
                    hash = mix(hash, 0xc0 | ch >> 6);
                    hash = mix(hash, 0x80 | ch & 0x3f);
                }
                else if (Character.isHighSurrogate(ch) && index + 1 < length && Character.isLowSurrogate(model.charAt(index + 1)))
                {
                    final int codePoint = Character.toCodePoint(ch, model.charAt(++index));
                    hash = mix(hash, 0xf0 | codePoint >> 18);
                    hash = mix(hash, 0x80 | codePoint >> 12 & 0x3f);
                    hash = mix(hash, 0x80 | codePoint >> 6 & 0x3f);
                    hash = mix(hash, 0x80 | codePoint & 0x3f);
                }
                else
                {
                    final int unit = Character.isSurrogate(ch) ? '?' : ch;
                    if (unit < 0x80)
                    {
                        hash = mix(hash, unit);
                    }
                    else
                    {
                        hash = mix(hash, 0xe0 | unit >> 12);
                        hash = mix(hash, 0x80 | unit >> 6 & 0x3f);
                        hash = mix(hash, 0x80 | unit & 0x3f);
                    }
                }
            }

            circuitId = nonZero(hash);
        }

        return circuitId;
    }

    static long nonZero(
        long hash)
    {
        return hash != DEFAULT_CIRCUIT_ID ? hash : 1L;
    }

    private static long mix(
        long hash,
        int octet)
    {
        return (hash ^ octet & 0xff) * PRIME;
    }

    private LlmCircuitId()
    {
    }
}
