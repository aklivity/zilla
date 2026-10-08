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

import jakarta.json.JsonException;

import org.junit.Test;

public class PresidioResponseTest
{
    @Test
    public void shouldNotDetectEmptyFindings()
    {
        assertThat(PresidioResponse.detected("[]"), equalTo(false));
    }

    @Test
    public void shouldDetectFindings()
    {
        String body =
            "[{\"analysis_explanation\":null,\"end\":33,\"entity_type\":\"EMAIL_ADDRESS\",\"score\":0.99,\"start\":13}]";

        assertThat(PresidioResponse.detected(body), equalTo(true));
    }

    @Test
    public void shouldDetectFindingsWithoutOptionalFields()
    {
        assertThat(PresidioResponse.detected("[{\"entity_type\":\"PERSON\",\"start\":0,\"end\":4}]"), equalTo(true));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectInvalidJson()
    {
        PresidioResponse.detected("not json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectObjectBody()
    {
        PresidioResponse.detected("{\"error\":\"No text provided\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBatchBody()
    {
        PresidioResponse.detected("[[]]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectFindingWithoutEntityType()
    {
        PresidioResponse.detected("[{\"start\":0,\"end\":4}]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectFindingWithoutStart()
    {
        PresidioResponse.detected("[{\"entity_type\":\"PERSON\",\"end\":4}]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectFindingWithoutEnd()
    {
        PresidioResponse.detected("[{\"entity_type\":\"PERSON\",\"start\":0}]");
    }
}
