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
package io.aklivity.zilla.runtime.model.json.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// The model-json adapter between the engine's ModelEnvelope, supplied to a pipeline by the caller driving
// it, and the JsonEnvelope a common-json stage reads and writes through its own controller. Both describe
// the same named, ordered, repeatable byte values, so this forwards each call unchanged; it exists only to
// bridge two vocabularies that cannot name each other. Bound once, as the pipeline is assembled, so no
// per-value or per-call adaptation happens on the hot path.
final class JsonModelEnvelope implements JsonEnvelope
{
    private final ModelEnvelope delegate;

    // NONE on either side is the same contract — reads empty, discards writes — so a caller that supplies
    // none leaves the stages reading common-json's own NONE rather than a wrapper around the engine's
    static JsonEnvelope of(
        ModelEnvelope envelope)
    {
        return envelope == ModelEnvelope.NONE ? JsonEnvelope.NONE : new JsonModelEnvelope(envelope);
    }

    private JsonModelEnvelope(
        ModelEnvelope delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public int count(
        String name)
    {
        return delegate.count(name);
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        return delegate.get(name, index);
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        delegate.set(name, value);
    }
}
