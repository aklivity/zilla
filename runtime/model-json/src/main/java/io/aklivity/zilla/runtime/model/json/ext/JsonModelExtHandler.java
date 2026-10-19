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
package io.aklivity.zilla.runtime.model.json.ext;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransformable;

/**
 * Appends whatever stages this extension contributes to an in-progress json pipeline, for one resolved
 * schema and configuration. A pipeline decoding a value for a reader and one encoding a caller's value
 * into its canonical form are extended independently, so an extension that only applies to one of these
 * overrides that method alone; the default for each leaves the other unaffected.
 */
public interface JsonModelExtHandler
{
    /**
     * Appends this extension's own stage or stages to {@code stream}, in data-flow order, for a pipeline
     * decoding the canonical value into the view delivered to a reader, for the given {@link JsonCache}
     * context. The default passes {@code stream} through unchanged for every context.
     * <p>
     * {@code cache} distinguishes a caller's relationship to a local cache, if any, from who ultimately
     * reads the transformed value -- see {@link JsonCache}. An extension with nothing reader-specific to
     * withhold when persisting a value ahead of any specific reader's request needs no override at all;
     * one that does distinguishes {@link JsonCache#WRITE} from the other two contexts itself.
     * </p>
     *
     * @param <T>     the caller's own concrete stream type
     * @param stream  the in-progress stream to extend
     * @param cache   the caller's relationship to a local cache, if any
     * @return the extended stream, as the same concrete type supplied
     */
    default <T extends JsonTransformable<T>> T decode(
        T stream,
        JsonCache cache)
    {
        return stream;
    }

    /**
     * Appends this extension's own stage or stages to {@code stream}, in data-flow order, for a pipeline
     * encoding a caller's value into its canonical form. The default passes {@code stream} through
     * unchanged.
     *
     * @param <T>     the caller's own concrete stream type
     * @param stream  the in-progress stream to extend
     * @return the extended stream, as the same concrete type supplied
     */
    default <T extends JsonTransformable<T>> T encode(
        T stream)
    {
        return stream;
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
        JsonSchema schema)
    {
        return 0;
    }
}
