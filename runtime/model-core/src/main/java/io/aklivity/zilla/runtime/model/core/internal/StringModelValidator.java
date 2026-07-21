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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.agrona.ExpandableDirectByteBuffer;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

final class StringModelValidator implements CoreModelValidator
{
    private final StringValidatorEncoding encoding;
    private final IntPredicate check;
    private final Pattern pattern;
    private final StringState state;
    private final ExpandableDirectByteBuffer buffer;

    static Supplier<CoreModelValidator> supplier(
        StringModelConfig config)
    {
        StringValidatorEncoding encoding = StringValidatorEncoding.of(config.encoding);
        int maxLength = config.maxLength;
        IntPredicate max = maxLength > 0 ? v -> v <= maxLength : v -> true;
        int minLength = config.minLength;
        IntPredicate min = minLength > 0 ? v -> v >= minLength : v -> true;
        IntPredicate check = max.and(min);
        Pattern pattern = config.pattern != null ? Pattern.compile(config.pattern) : null;
        return () -> new StringModelValidator(encoding, check, pattern);
    }

    private StringModelValidator(
        StringValidatorEncoding encoding,
        IntPredicate check,
        Pattern pattern)
    {
        this.encoding = encoding;
        this.check = check;
        this.pattern = pattern;
        this.state = new StringState();
        this.buffer = new ExpandableDirectByteBuffer();
    }

    @Override
    public Validity validate(
        int flags,
        DirectBufferEx data,
        int index,
        int length)
    {
        if ((flags & FLAGS_INIT) != 0x00)
        {
            state.processed = 0;
            state.length = 0;
        }

        if (pattern != null)
        {
            buffer.putBytes(state.length, data, index, length);
        }

        // an encoding failure (bad UTF-8/16, or a value left partially decoded across the boundary) is a
        // structural decode failure: MALFORMED, never relaxed
        boolean decoded = encoding.validate(state, flags, data, index, length);
        Validity validity = decoded ? Validity.VALID : Validity.MALFORMED;

        if ((flags & FLAGS_FIN) != 0x00 && validity == Validity.VALID)
        {
            boolean complete = state.processed == 0;
            if (!complete)
            {
                validity = Validity.MALFORMED;
            }
            else
            {
                // a fully-decoded string that violates the length bounds or the pattern is a semantic
                // constraint failure: INVALID, relaxable under LENIENT
                boolean matches = pattern == null ||
                    pattern.matcher(buffer.getStringWithoutLengthUtf8(0, state.length)).matches();
                validity = matches && check.test(state.length) ? Validity.VALID : Validity.INVALID;
            }
        }

        return validity;
    }
}
