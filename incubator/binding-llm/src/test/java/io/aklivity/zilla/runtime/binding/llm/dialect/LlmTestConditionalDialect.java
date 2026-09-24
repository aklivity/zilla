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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public final class LlmTestConditionalDialect implements LlmDialect
{
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String METHOD_POST = "POST";
    private static final String PATH_TEST = "/v1/test";

    private static final String MODEL_PATH = "$.model";
    private static final String MODEL_NAME = "model";

    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public boolean detect(
        ModelEnvelope headers)
    {
        return METHOD_POST.equals(header(headers, HEADER_METHOD)) && PATH_TEST.equals(header(headers, HEADER_PATH));
    }

    @Override
    public ModelTransform supplyDecoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new ModelExtractTransform(MODEL_PATH, MODEL_NAME, envelope) : ModelTransform.NONE;
    }

    @Override
    public ModelTransform supplyValidator(
        Kind kind,
        ModelEnvelope envelope)
    {
        return supplyDecoder(kind, envelope);
    }

    @Override
    public ModelTransform supplyEncoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        return ModelTransform.NONE;
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return null;
    }

    private static String header(
        ModelEnvelope headers,
        String name)
    {
        DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    // mirrors KafkaExtractTransform (runtime/binding-kafka/.../cache/KafkaExtractTransform.java): observes
    // the field at path, copies its value into envelope under name, forwards the field unchanged
    private static final class ModelExtractTransform implements ModelTransform
    {
        private final String path;
        private final String name;
        private final ModelEnvelope envelope;

        private ModelExtractTransform(
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
}
