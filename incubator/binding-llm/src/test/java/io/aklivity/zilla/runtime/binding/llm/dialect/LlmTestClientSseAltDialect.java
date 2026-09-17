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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import java.net.URL;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * A second test-only dialect, distinct from {@link LlmTestClientSseDialect} only by {@link #name()}, so an
 * {@code llm client}'s cross-dialect branch (inbound dialect differs from the configured dialect) can be
 * exercised without depending on a real second dialect implementation (e.g. anthropic). Both directions'
 * transforms are still identity, so this proves the dialect-comparison branch and the chained pipeline
 * plumbing without claiming semantic translation.
 */
public final class LlmTestClientSseAltDialect implements LlmDialect
{
    @Override
    public String name()
    {
        return "test-client-sse-alt";
    }

    @Override
    public boolean detect(
        ModelEnvelope headers)
    {
        return false;
    }

    @Override
    public ModelTransform supplyDecoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        return IdentityTransform.INSTANCE;
    }

    @Override
    public ModelTransform supplyEncoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        return IdentityTransform.INSTANCE;
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return null;
    }

    static URL schemaResource()
    {
        return LlmTestClientSseAltDialect.class.getResource("test.client.sse.schema.json");
    }

    private static final class IdentityTransform implements ModelTransform
    {
        private static final IdentityTransform INSTANCE = new IdentityTransform();

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
