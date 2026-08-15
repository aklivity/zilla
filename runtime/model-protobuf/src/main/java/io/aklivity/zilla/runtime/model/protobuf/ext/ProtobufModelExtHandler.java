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
package io.aklivity.zilla.runtime.model.protobuf.ext;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransformable;

/**
 * Appends whatever stages this extension contributes to an in-progress protobuf pipeline, for one resolved
 * schema and configuration. A pipeline decoding the canonical value into the view delivered to a reader
 * and one encoding a caller's value into its canonical form are extended independently, so an extension
 * that only applies to one direction overrides that method alone; the default leaves the other direction
 * unchanged.
 */
public interface ProtobufModelExtHandler
{
    /**
     * Appends this extension's own stage or stages to {@code transformable}, in data-flow order, for a
     * pipeline decoding the canonical value into the view delivered to a reader. The default passes
     * {@code transformable} through unchanged.
     *
     * @param <T>              the caller's own concrete stream type
     * @param transformable    the in-progress stream to extend
     * @return the extended stream, as the same concrete type supplied
     */
    default <T extends ProtobufTransformable<T>> T decode(
        T transformable)
    {
        return transformable;
    }

    /**
     * Appends this extension's own stage or stages to {@code transformable}, in data-flow order, for a
     * pipeline encoding a caller's value into its canonical form. The default passes {@code transformable}
     * through unchanged.
     *
     * @param <T>              the caller's own concrete stream type
     * @param transformable    the in-progress stream to extend
     * @return the extended stream, as the same concrete type supplied
     */
    default <T extends ProtobufTransformable<T>> T encode(
        T transformable)
    {
        return transformable;
    }

    /**
     * Returns the maximum number of additional bytes this extension's transform may add to a decoded
     * value of {@code schema}, beyond what the untransformed value would occupy — for example, a
     * substitute value whose length does not derive from the original field's length. A caller sizing a
     * buffer to hold the transformed output adds this to its own estimate.
     *
     * @param schema  the resolved schema
     * @return the additional byte count (0 if this extension's transform never expands a value)
     */
    default int padding(
        ProtobufSchema schema)
    {
        return 0;
    }
}
