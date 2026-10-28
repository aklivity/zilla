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

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;

import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCard;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class A2aDialectV10 implements A2aDialect
{
    static final String VERSION = "1.0";

    private final A2aDialectEncoder encoder = new A2aDialectEncoder();
    private final A2aTenantInjector tenants = new A2aTenantInjector();

    @Override
    public String version()
    {
        return VERSION;
    }

    @Override
    public boolean supports(
        int kind)
    {
        return true;
    }

    @Override
    public int encodeRequest(
        A2aBeginExFW beginEx,
        String tenant,
        MutableDirectBufferEx output,
        int offset)
    {
        encoder.wrap(output, offset);

        switch (beginEx.kind())
        {
        case KIND_MESSAGE_SEND:
            encoder.method("SendMessage").params();
            break;
        case KIND_MESSAGE_STREAM:
            encoder.method("SendStreamingMessage").params();
            break;
        case KIND_TASKS_LIST:
            encoder.method("ListTasks").params();
            break;
        case KIND_PUSH_CONFIG_CREATE:
            encoder.method("CreateTaskPushNotificationConfig").params();
            break;
        case KIND_TASKS_GET:
            encoder.method("GetTask").params().object(tenant).member("id", beginEx.tasksGet().taskId().asString());
            if (beginEx.tasksGet().historyLength() >= 0)
            {
                encoder.member("historyLength", beginEx.tasksGet().historyLength());
            }
            encoder.endObject().end();
            break;
        case KIND_TASKS_CANCEL:
            encoder.method("CancelTask").params().object(tenant)
                .member("id", beginEx.tasksCancel().taskId().asString()).endObject().end();
            break;
        case KIND_TASKS_SUBSCRIBE:
            encoder.method("SubscribeToTask").params().object(tenant)
                .member("id", beginEx.tasksSubscribe().taskId().asString()).endObject().end();
            break;
        case KIND_PUSH_CONFIG_GET:
            encoder.method("GetTaskPushNotificationConfig").params().object(tenant)
                .member("taskId", beginEx.pushConfigGet().taskId().asString())
                .member("id", beginEx.pushConfigGet().configId().asString()).endObject().end();
            break;
        case KIND_PUSH_CONFIG_LIST:
            encoder.method("ListTaskPushNotificationConfigs").params().object(tenant)
                .member("taskId", beginEx.pushConfigList().taskId().asString()).endObject().end();
            break;
        case KIND_PUSH_CONFIG_DELETE:
            encoder.method("DeleteTaskPushNotificationConfig").params().object(tenant)
                .member("taskId", beginEx.pushConfigDelete().taskId().asString())
                .member("id", beginEx.pushConfigDelete().configId().asString()).endObject().end();
            break;
        case KIND_EXTENDED_AGENT_CARD:
        default:
            encoder.method("GetExtendedAgentCard").end();
            break;
        }

        return encoder.limit();
    }

    @Override
    public boolean translatesRequest(
        String tenant)
    {
        return tenant != null;
    }

    @Override
    public int translateRequest(
        int kind,
        String tenant,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        return tenants.inject(tenant, input, inputOffset, inputLimit, output, outputOffset);
    }

    @Override
    public boolean translatesResult()
    {
        return false;
    }

    @Override
    public int translateResult(
        int kind,
        A2aAgentCard card,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        throw new UnsupportedOperationException();
    }
}
