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

final class LlmOpenaiOptions
{
    static final int RESPONSE_CREATED = 0;
    static final int RESPONSE_SERVICE_TIER = 1;
    static final int RESPONSE_SYSTEM_FINGERPRINT = 2;

    private static final String[] RESPONSE_MEMBERS =
    {
        "created", "service_tier", "system_fingerprint"
    };

    static LlmResponseOptions response()
    {
        return new LlmResponseOptions(RESPONSE_MEMBERS);
    }

    private LlmOpenaiOptions()
    {
    }
}
