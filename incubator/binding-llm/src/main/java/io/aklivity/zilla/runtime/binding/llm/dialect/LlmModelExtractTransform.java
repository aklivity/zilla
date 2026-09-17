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

import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * Observes the top-level {@code model} field of a request -- {@code $.model} in both the OpenAI Chat
 * Completions and Anthropic Messages request shapes -- copying its value into the supplied
 * {@link ModelEnvelope} while forwarding every field unchanged, at any depth: no canonical renaming.
 * Mirrors how a Kafka cache model's {@code extractKey}/{@code extractHeaders} transform observes a field
 * and copies its value into an envelope while it flows through unchanged.
 * <p>
 * {@code model} sits at the identical top-level path in every dialect this binding supports so far, so one
 * dialect-neutral instance backs every {@link LlmDialect#supplyValidator(LlmDialect.Kind, ModelEnvelope)}
 * implementation rather than duplicating identical extraction logic per dialect.
 * </p>
 */
final class LlmModelExtractTransform implements ModelTransform
{
    private static final String MODEL_PATH = "$.model";
    private static final String MODEL_NAME = "model";

    private final ModelEnvelope envelope;

    LlmModelExtractTransform(
        ModelEnvelope envelope)
    {
        this.envelope = envelope;
    }

    @Override
    public ModelStatus transform(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        if (event == ModelEvent.FIELD && MODEL_PATH.equals(source.getPath()))
        {
            envelope.set(MODEL_NAME, source.getValue());
        }

        return sink.transform(control, source, event);
    }

    @Override
    public boolean identity()
    {
        return true;
    }
}
