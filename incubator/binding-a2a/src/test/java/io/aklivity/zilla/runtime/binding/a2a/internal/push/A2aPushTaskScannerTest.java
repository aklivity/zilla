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

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aPushTaskScanner.Result;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aPushTaskScannerTest
{
    private final A2aPushTaskScanner scanner = new A2aPushTaskScanner();

    @Test
    public void shouldFindTaskIdOfEachEventKind()
    {
        assertThat(scan("{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\"}}"), equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-1"));

        assertThat(scan("{\"artifactUpdate\":{\"contextId\":\"c-1\",\"artifact\":{\"id\":\"a-1\"},\"taskId\":\"t-2\"}}"),
            equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-2"));

        assertThat(scan("{\"message\":{\"messageId\":\"m-1\",\"taskId\":\"t-3\"}}"), equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-3"));

        assertThat(scan("{\"task\":{\"id\":\"t-4\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}"), equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-4"));
    }

    @Test
    public void shouldFindTaskIdOfFlatEvents()
    {
        assertThat(scan("{\"kind\":\"status-update\",\"taskId\":\"t-5\"}"), equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-5"));

        assertThat(scan("{\"kind\":\"task\",\"id\":\"t-6\"}"), equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-6"));
    }

    @Test
    public void shouldNotMistakeNestedIdsForTaskId()
    {
        assertThat(scan("{\"artifactUpdate\":{\"artifact\":{\"taskId\":\"nested\"}}}"), equalTo(Result.MISSING));
        assertThat(scan("{\"statusUpdate\":{\"id\":\"not-a-task\"}}"), equalTo(Result.MISSING));
        assertThat(scanner.taskId(), nullValue());
    }

    @Test
    public void shouldReportIncompleteUntilTaskIdArrives()
    {
        final String body = "{\"statusUpdate\":{\"contextId\":\"c-1\",\"taskId\":\"t-1\",\"status\":{}}}";

        for (int length = 1; length < body.indexOf("t-1\"") + 4; length++)
        {
            assertThat(body.substring(0, length), scan(body.substring(0, length), false), equalTo(Result.INCOMPLETE));
        }

        assertThat(scan(body.substring(0, body.indexOf("t-1\"") + 4), false), equalTo(Result.FOUND));
        assertThat(scanner.taskId(), equalTo("t-1"));
    }

    @Test
    public void shouldReportMissingWhenBodyEndsWithoutTaskId()
    {
        assertThat(scan("{\"statusUpdate\":{\"contextId\":\"c-1\"}}"), equalTo(Result.MISSING));
        assertThat(scan("{\"statusUpdate\":{\"taskId\":42}}"), equalTo(Result.MISSING));
        assertThat(scan("{\"statusUpdate\":"), equalTo(Result.MISSING));
        assertThat(scan("not json"), equalTo(Result.MISSING));
    }

    private Result scan(
        String text)
    {
        return scan(text, true);
    }

    private Result scan(
        String text,
        boolean last)
    {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return scanner.scan(new UnsafeBufferEx(bytes), 0, bytes.length, last);
    }
}
