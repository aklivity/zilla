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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmContentTypesTest
{
    @Test
    public void shouldReturnMediaTypeWithoutParameters()
    {
        assertThat(LlmContentTypes.mediaType("application/json; charset=utf-8"), equalTo("application/json"));
    }

    @Test
    public void shouldReturnMediaTypeWhenNoParameters()
    {
        assertThat(LlmContentTypes.mediaType("text/event-stream"), equalTo("text/event-stream"));
    }

    @Test
    public void shouldReturnNullWhenContentTypeAbsent()
    {
        assertThat(LlmContentTypes.mediaType(null), nullValue());
    }
}
