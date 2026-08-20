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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// The model-protobuf adapter between the engine's ModelEnvelope, supplied to a pipeline by the caller driving
// it, and the ProtobufEnvelope a common-protobuf stage reads and writes through its own controller. Both describe
// the same named, ordered, repeatable byte values, so this forwards each call unchanged; it exists only to
// bridge two vocabularies that cannot name each other. Bound once, as the pipeline is assembled, so no
// per-value or per-call adaptation happens on the hot path.
final class ProtobufModelEnvelope implements ProtobufEnvelope
{
    private final ModelEnvelope delegate;

    // NONE on either side is the same contract — reads empty, discards writes — so a caller that supplies
    // none leaves the stages reading common-protobuf's own NONE rather than a wrapper around the engine's
    static ProtobufEnvelope of(
        ModelEnvelope envelope)
    {
        return envelope == ModelEnvelope.NONE ? ProtobufEnvelope.NONE : new ProtobufModelEnvelope(envelope);
    }

    private ProtobufModelEnvelope(
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
