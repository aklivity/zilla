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
package io.aklivity.zilla.runtime.binding.llm.internal.codec;

import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmJsonContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.internal.encode.LlmContentEncoder;
import io.aklivity.zilla.runtime.binding.llm.internal.encode.LlmJsonContentEncoder;

public final class LlmJsonContentCodecSpi implements LlmContentCodecSpi
{
    @Override
    public String contentType()
    {
        return "application/json";
    }

    @Override
    public LlmContentDecoder supplyDecoder()
    {
        return new LlmJsonContentDecoder();
    }

    @Override
    public LlmContentEncoder supplyEncoder()
    {
        return new LlmJsonContentEncoder();
    }
}
