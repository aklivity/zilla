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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class LlmCapturedScalarTest
{
    private static final int LIMIT = 4096;

    private final LlmCapturedScalar scalar = new LlmCapturedScalar();

    @Test
    public void shouldHoldString()
    {
        assertThat(scalar.add(JsonEvent.VALUE_STRING, "high", LIMIT), is(true));

        assertThat(scalar.event(), equalTo(JsonEvent.VALUE_STRING));
        assertThat(scalar.text(), equalTo("high"));
        assertThat(scalar.string(), equalTo("high"));
    }

    @Test
    public void shouldHoldNumberWithoutViewingItAsString()
    {
        assertThat(scalar.add(JsonEvent.VALUE_NUMBER, "42", LIMIT), is(true));

        assertThat(scalar.event(), equalTo(JsonEvent.VALUE_NUMBER));
        assertThat(scalar.text(), equalTo("42"));
        assertThat(scalar.string(), nullValue());
    }

    @Test
    public void shouldHoldLiteralWithoutText()
    {
        assertThat(scalar.add(JsonEvent.VALUE_FALSE, null, LIMIT), is(true));

        assertThat(scalar.event(), equalTo(JsonEvent.VALUE_FALSE));
        assertThat(scalar.text(), nullValue());
        assertThat(scalar.string(), nullValue());
    }

    @Test
    public void shouldKeepOnlyFirstEventOfContainer()
    {
        assertThat(scalar.add(JsonEvent.START_OBJECT, null, LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.KEY_NAME, "a", LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.VALUE_STRING, "b", LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.END_OBJECT, null, LIMIT), is(true));

        assertThat(scalar.event(), equalTo(JsonEvent.START_OBJECT));
        assertThat(scalar.text(), nullValue());
        assertThat(scalar.string(), nullValue());
    }

    @Test
    public void shouldAcceptScalarFillingLimitExactly()
    {
        assertThat(scalar.add(JsonEvent.VALUE_STRING, "x".repeat(LIMIT - 4), LIMIT), is(true));
        assertThat(scalar.string().length(), equalTo(LIMIT - 4));
    }

    @Test
    public void shouldRejectScalarOneOverLimit()
    {
        assertThat(scalar.add(JsonEvent.VALUE_STRING, "x".repeat(LIMIT - 3), LIMIT), is(false));
        assertThat(scalar.event(), nullValue());
        assertThat(scalar.string(), nullValue());
    }

    @Test
    public void shouldCountEveryEventOfContainerAgainstLimit()
    {
        assertThat(scalar.add(JsonEvent.START_OBJECT, null, LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.KEY_NAME, "k", LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.VALUE_STRING, "x".repeat(LIMIT - 20), LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.END_OBJECT, null, LIMIT), is(true));
        assertThat(scalar.add(JsonEvent.VALUE_TRUE, null, LIMIT), is(false));
    }

    @Test
    public void shouldForgetValueWhenCleared()
    {
        scalar.add(JsonEvent.VALUE_STRING, "x".repeat(LIMIT - 4), LIMIT);
        scalar.clear();

        assertThat(scalar.event(), nullValue());
        assertThat(scalar.text(), nullValue());
        assertThat(scalar.add(JsonEvent.VALUE_STRING, "y".repeat(LIMIT - 4), LIMIT), is(true));
        assertThat(scalar.string().charAt(0), equalTo('y'));
    }
}
