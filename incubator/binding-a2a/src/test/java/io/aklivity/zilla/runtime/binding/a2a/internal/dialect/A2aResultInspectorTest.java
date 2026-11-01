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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aResultInspectorTest
{
    private final A2aResultInspector inspector = new A2aResultInspector();
    private final UnsafeBufferEx input = new UnsafeBufferEx(new byte[8192]);

    @Test
    public void shouldInspectMessage()
    {
        assertThat(inspect(KIND_MESSAGE_SEND, "{\"message\":{\"messageId\":\"m-2\",\"taskId\":\"ignored\"}}"), is(true));
        assertThat(inspector.kind, equalTo(A2aDataExFW.KIND_MESSAGE));
        assertThat(inspector.taskId, nullValue());
    }

    @Test
    public void shouldInspectTaskResult()
    {
        assertThat(inspect(KIND_MESSAGE_SEND,
            "{\"task\":{\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}"), is(true));
        assertThat(inspector.kind, equalTo(A2aDataExFW.KIND_TASK));
        assertThat(inspector.taskId, equalTo("t-1"));
        assertThat(inspector.contextId, equalTo("c-1"));
        assertThat(inspector.state, equalTo("COMPLETED"));
    }

    @Test
    public void shouldInspectStatusUpdate()
    {
        assertThat(inspect(KIND_MESSAGE_STREAM,
            "{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}"),
            is(true));
        assertThat(inspector.kind, equalTo(A2aDataExFW.KIND_STATUS_UPDATE));
        assertThat(inspector.taskId, equalTo("t-1"));
        assertThat(inspector.state, equalTo("WORKING"));
    }

    @Test
    public void shouldInspectArtifactUpdate()
    {
        assertThat(inspect(KIND_MESSAGE_STREAM,
            "{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"artifact\":{\"artifactId\":\"a-1\"," +
            "\"parts\":[{\"text\":\"x\"}]}}}"), is(true));
        assertThat(inspector.kind, equalTo(A2aDataExFW.KIND_ARTIFACT_UPDATE));
        assertThat(inspector.artifactId, equalTo("a-1"));
    }

    @Test
    public void shouldInspectDirectTask()
    {
        assertThat(inspect(KIND_TASKS_GET, "{\"id\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}"), is(true));
        assertThat(inspector.taskId, equalTo("t-1"));
        assertThat(inspector.state, equalTo("WORKING"));
        assertThat(inspect(KIND_TASKS_CANCEL, "{\"id\":\"t-2\",\"status\":{\"state\":\"CANCELED\"}}"), is(true));
        assertThat(inspector.state, equalTo("CANCELED"));
    }

    @Test
    public void shouldNotInspectUndescribedResults()
    {
        assertThat(inspect(KIND_PUSH_CONFIG_GET, "{\"id\":\"c-1\"}"), is(false));
        assertThat(inspect(KIND_MESSAGE_SEND, "{\"unknown\":{}}"), is(false));
        assertThat(inspector.kind, equalTo(A2aResultInspector.NO_KIND));
    }

    private boolean inspect(
        int operation,
        String result)
    {
        final byte[] bytes = result.getBytes(UTF_8);
        input.putBytes(0, bytes);
        return inspector.inspect(operation, input, 0, bytes.length);
    }
}
