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

    static final int RESPONSE_STOP_SEQUENCE = 0;

    private static final String[] REQUEST_MEMBERS =
    {
        "stream", "temperature", "top_p", "top_k", "max_tokens", "stop_sequences", "output_config"
    };

    private static final String[] RESPONSE_MEMBERS =
    {
        "stop_sequence"
    };

    static LlmOptions request()
    {
        return new LlmOptions(REQUEST_MEMBERS);
    }

    static LlmResponseOptions response()
    {
        return new LlmResponseOptions(RESPONSE_MEMBERS);
    }

    private LlmAnthropicOptions()
    {
    }
}
