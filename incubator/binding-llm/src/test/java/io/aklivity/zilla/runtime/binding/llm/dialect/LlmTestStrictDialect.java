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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// Test fixture: detects unambiguously via a fixed content-type, contributes a request schema that requires
// a "prompt" property -- genuinely rejects a payload missing it -- so k3po ITs can exercise schema
// enforcement (ModelStatus.REJECTED) independently of dialect detection.
public final class LlmTestStrictDialect implements LlmDialect
{
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String CONTENT_TYPE = "application/vnd.zilla.test-strict+json";

    @Override
    public String name()
    {
        return "test-strict";
    }

    @Override
    public boolean detect(
        ModelEnvelope headers)
    {
        return CONTENT_TYPE.equals(header(headers, HEADER_CONTENT_TYPE));
    }

    @Override
    public ModelTransform supplyDecoder(
        Kind kind,
        ModelEnvelope envelope)
    {
        return ModelTransform.NONE;
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

    static URL schemaResource()
    {
        return LlmTestStrictDialect.class.getResource("test.strict.request.schema.json");
    }

    private static String header(
        ModelEnvelope headers,
        String name)
    {
        DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }
}
