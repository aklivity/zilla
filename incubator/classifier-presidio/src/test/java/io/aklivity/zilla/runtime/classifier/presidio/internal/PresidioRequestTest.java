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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.junit.Test;

public class PresidioRequestTest
{
    @Test
    public void shouldBuildBodyWithThreshold()
    {
        String body = PresidioRequest.body("call jane", "en", List.of("PERSON", "EMAIL_ADDRESS"), 0.6);

        assertThat(body, equalTo(
            "{\"text\":\"call jane\",\"language\":\"en\",\"entities\":[\"PERSON\",\"EMAIL_ADDRESS\"],\"score_threshold\":0.6}"));
    }

    @Test
    public void shouldBuildBodyWithoutThreshold()
    {
        String body = PresidioRequest.body("call jane", "es", List.of("PERSON"), 0.0);

        assertThat(body, equalTo("{\"text\":\"call jane\",\"language\":\"es\",\"entities\":[\"PERSON\"]}"));
    }

    @Test
    public void shouldEscapeText()
    {
        String body = PresidioRequest.body("say \"hi\"\nthen \\ go", "en", List.of("PERSON"), 0.0);

        assertThat(body, equalTo(
            "{\"text\":\"say \\\"hi\\\"\\nthen \\\\ go\",\"language\":\"en\",\"entities\":[\"PERSON\"]}"));
    }
}
