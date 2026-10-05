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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

final class LlmAnthropicOptions
{
    static final int STREAM = 0;
    static final int TEMPERATURE = 1;
    static final int TOP_P = 2;
    static final int TOP_K = 3;
    static final int MAX_TOKENS = 4;
    static final int STOP_SEQUENCES = 5;

    static final int RESPONSE_STOP_SEQUENCE = 0;
    static final int RESPONSE_CONTAINER_ID = 1;
    static final int RESPONSE_COUNT = 2;

    private static final String[] MEMBERS =
    {
        "stream", "temperature", "top_p", "top_k", "max_tokens", "stop_sequences"
    };

    static LlmOptions request()
    {
        return new LlmOptions(MEMBERS);
    }

    private LlmAnthropicOptions()
    {
    }
}
