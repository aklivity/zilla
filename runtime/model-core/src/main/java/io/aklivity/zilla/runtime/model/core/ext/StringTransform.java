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
package io.aklivity.zilla.runtime.model.core.ext;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * A whole-value stage in a {@code string} pipeline: {@code string} has no internal field structure, so a
 * stage sees the complete decoded value (encoded per the model's configured {@code encoding}) in one call,
 * rather than a stream of per-field events. Stages compose left-to-right via
 * {@link StringTransformable#transform(StringTransform)}.
 */
public interface StringTransform
{
    /**
     * A value too small to be delivered downstream at all, returned by {@link #transform} to signal that
     * this value should not be forwarded (for example, an installed extension's own decision to withhold
     * it entirely).
     */
    int OMIT = -1;

    /**
     * Identity stage that forwards the value unchanged. {@link StringTransformable#transform(StringTransform)}
     * drops it rather than binding it, so a caller with nothing to insert passes this instead of branching.
     */
    StringTransform NONE = new StringTransform()
    {
        @Override
        public int transform(
            DirectBufferEx value,
            int index,
            int length,
            MutableDirectBufferEx dst,
            int dstIndex)
        {
            dst.putBytes(dstIndex, value, index, length);
            return length;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    };

    /**
     * Applies this stage to one complete decoded value, writing the result to {@code dst}.
     *
     * @param value     the buffer holding the complete input value
     * @param index     the offset of the input value within {@code value}
     * @param length    the length of the input value
     * @param dst       the destination buffer for the output value
     * @param dstIndex  the offset within {@code dst} to write the output value
     * @return the length written to {@code dst}, or {@link #OMIT} if this value should not be delivered
     */
    int transform(
        DirectBufferEx value,
        int index,
        int length,
        MutableDirectBufferEx dst,
        int dstIndex);

    /**
     * Returns the maximum number of additional bytes this stage's transform may add to a value, beyond
     * what the untransformed value would occupy — for example, a substitute value whose length does not
     * derive from the original value's length. A caller sizing a buffer to hold the transformed output
     * adds this to its own estimate.
     *
     * @return the additional byte count (0 if this stage's transform never expands a value)
     */
    default int padding()
    {
        return 0;
    }

    /**
     * Whether this stage forwards every value verbatim, leaving the bytes unchanged. A validating or
     * observing stage is identity; a stage that substitutes, drops, or rewrites values is not.
     */
    default boolean identity()
    {
        return false;
    }
}
