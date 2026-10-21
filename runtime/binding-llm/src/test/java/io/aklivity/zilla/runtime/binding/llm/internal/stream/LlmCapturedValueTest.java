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

public class LlmCapturedValueTest
{
    private static final int LIMIT = 4096;

    private final LlmCapturedValue value = new LlmCapturedValue();

    @Test
    public void shouldFindMemberOfObject()
    {
        object("a", "1", "b", "2");

        assertThat(value.member(0, "b"), equalTo(4));
        assertThat(value.string(value.member(0, "b")), equalTo("2"));
    }

    @Test
    public void shouldSkipNestedValuesWhenFindingMember()
    {
        value.add(JsonEvent.START_OBJECT, null, LIMIT);
        value.add(JsonEvent.KEY_NAME, "a", LIMIT);
        value.add(JsonEvent.START_ARRAY, null, LIMIT);
        value.add(JsonEvent.KEY_NAME, "b", LIMIT);
        value.add(JsonEvent.END_ARRAY, null, LIMIT);
        value.add(JsonEvent.KEY_NAME, "b", LIMIT);
        value.add(JsonEvent.VALUE_STRING, "found", LIMIT);
        value.add(JsonEvent.END_OBJECT, null, LIMIT);

        assertThat(value.string(value.member(0, "b")), equalTo("found"));
    }

    @Test
    public void shouldReportAbsentMember()
    {
        object("a", "1");

        assertThat(value.member(0, "z"), equalTo(LlmCapturedValue.ABSENT));
        assertThat(value.string(LlmCapturedValue.ABSENT), nullValue());
        assertThat(value.member(1, "a"), equalTo(LlmCapturedValue.ABSENT));
    }

    @Test
    public void shouldFindEndOfValue()
    {
        object("a", "1");

        assertThat(value.end(0), equalTo(value.count()));
        assertThat(value.end(2), equalTo(3));
    }

    @Test
    public void shouldFindEndOfScalar()
    {
        value.add(JsonEvent.VALUE_STRING, "auto", LIMIT);

        assertThat(value.end(0), equalTo(1));
    }

    @Test
    public void shouldRestrictMembers()
    {
        object("a", "1", "b", "2");

        assertThat(value.onlyMembers(0, new String[] {"a", "b", "c"}), is(true));
        assertThat(value.onlyMembers(0, new String[] {"a"}), is(false));
        assertThat(value.onlyMembers(2, new String[] {"a"}), is(false));
    }

    @Test
    public void shouldRefuseValueBeyondLimit()
    {
        assertThat(value.add(JsonEvent.VALUE_STRING, "x".repeat(10), 20), is(true));
        assertThat(value.add(JsonEvent.VALUE_STRING, "x".repeat(10), 20), is(false));
        assertThat(value.count(), equalTo(1));
    }

    @Test
    public void shouldGrowBeyondInitialCapacity()
    {
        value.add(JsonEvent.START_ARRAY, null, LIMIT);

        for (int index = 0; index < 100; index++)
        {
            value.add(JsonEvent.VALUE_STRING, "n" + index, LIMIT);
        }

        value.add(JsonEvent.END_ARRAY, null, LIMIT);

        assertThat(value.count(), equalTo(102));
        assertThat(value.end(0), equalTo(102));
        assertThat(value.text(100), equalTo("n99"));
    }

    @Test
    public void shouldClear()
    {
        object("a", "1");
        value.clear();

        assertThat(value.count(), equalTo(0));
        assertThat(value.event(0), nullValue());
    }

    private void object(
        String... pairs)
    {
        value.add(JsonEvent.START_OBJECT, null, LIMIT);

        for (int index = 0; index < pairs.length; index += 2)
        {
            value.add(JsonEvent.KEY_NAME, pairs[index], LIMIT);
            value.add(JsonEvent.VALUE_STRING, pairs[index + 1], LIMIT);
        }

        value.add(JsonEvent.END_OBJECT, null, LIMIT);
    }
}
