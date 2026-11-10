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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmGeminiOperationTest
{
    private static final String[] PATHS =
    {
        "/v1/models/gemini-pro:generateContent",
        "/v1beta/models/gemini-pro:streamGenerateContent",
        "/v1beta/models/gemini-pro:streamGenerateContent?alt=sse",
        "/v1beta/models/gemini-pro:generateContent?",
        "/v1beta/models/gemini-pro:generateContent?key=a&b=c/d?e",
        "/v1/models/gemini%2Dpro:generateContent",
        "/v1/models/a:b:generateContent",
        "/v1/models/:generateContent",
        "/v1/models/a:generateContent",
        "/v1/models/a%2:generateContent",
        "/v1/models/a%zz:generateContent",
        "/v1/models/a%:generateContent",
        "/v1/models/a/b:generateContent",
        "/v1/models/a?b:generateContent",
        "/v2/models/a:generateContent",
        "/v1gamma/models/a:generateContent",
        "/v1/model/a:generateContent",
        "/v1/models/a:generateContentX",
        "/v1/models/a:generateContent/",
        "/v1/models/a:generateconTent",
        "/v1/models/a:embedContent",
        "v1/models/a:generateContent",
        "/v1/models/a:generateContent?x\ny",
        "/v1/models/a:generateContent\n",
        "/v1/chat/completions",
        "/v1/models/",
        "/v1/models",
        "/v1",
        "/",
        ""
    };

    @Test
    public void shouldRecognizeOperationsLikeThePathPattern()
    {
        for (String path : PATHS)
        {
            assertThat(path, LlmGeminiServerFactory.operates(new UnsafeBufferEx(path.getBytes(UTF_8))),
                equalTo(LlmGeminiServerFactory.operation(path) != null));
        }
    }

    @Test
    public void shouldNotRecognizeAbsentPath()
    {
        assertThat(LlmGeminiServerFactory.operates(null), equalTo(false));
    }
}
