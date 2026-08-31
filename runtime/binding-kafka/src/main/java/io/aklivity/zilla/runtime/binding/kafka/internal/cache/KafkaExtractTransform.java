/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// One stage per extractKey / extractHeaders config entry: on the field at path, copies its value into
// envelope under name -- ":key" for extractKey, so KafkaCachePartition can read the override back off the
// key model's own envelope in place of the persisted key; the configured header name for extractHeaders,
// read back off the value model's trailers envelope. Purely observing -- the field itself flows on to the
// sink untouched -- so this stage is always identity, and multiple entries compose the same way any
// ModelTransform chain does, via andThen.
public final class KafkaExtractTransform implements ModelTransform
{
    private final String path;
    private final String name;
    private final ModelEnvelope envelope;

    public KafkaExtractTransform(
        String path,
        String name,
        ModelEnvelope envelope)
    {
        this.path = path;
        this.name = name;
        this.envelope = envelope;
    }

    @Override
    public ModelStatus transform(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        if (event == ModelEvent.FIELD && path.equals(source.getPath()))
        {
            envelope.set(name, source.getValue());
        }

        return sink.transform(control, source, event);
    }

    @Override
    public boolean identity()
    {
        return true;
    }
}
