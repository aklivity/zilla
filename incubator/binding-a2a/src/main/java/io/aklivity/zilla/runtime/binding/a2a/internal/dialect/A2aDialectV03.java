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

final class A2aDialectV03 implements A2aDialect
{
    static final String VERSION = "0.3";

    private final A2aDialectEncoder encoder = new A2aDialectEncoder();
    private final A2aV03Translator translator = new A2aV03Translator();

    @Override
    public String version()
    {
        return VERSION;
    }

    @Override
    public boolean supports(
        int kind)
    {
        return kind != KIND_TASKS_LIST;
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
            encoder.method("message/send").params();
            break;
        case KIND_MESSAGE_STREAM:
            encoder.method("message/stream").params();
            break;
        case KIND_PUSH_CONFIG_CREATE:
            encoder.method("tasks/pushNotificationConfig/set").params();
            break;
        case KIND_TASKS_GET:
            encoder.method("tasks/get").params().object(null).member("id", beginEx.tasksGet().taskId().asString());
            if (beginEx.tasksGet().historyLength() >= 0)
            {
                encoder.member("historyLength", beginEx.tasksGet().historyLength());
            }
            encoder.endObject().end();
            break;
        case KIND_TASKS_CANCEL:
            encoder.method("tasks/cancel").params().object(null)
                .member("id", beginEx.tasksCancel().taskId().asString()).endObject().end();
            break;
        case KIND_TASKS_SUBSCRIBE:
            encoder.method("tasks/resubscribe").params().object(null)
                .member("id", beginEx.tasksSubscribe().taskId().asString()).endObject().end();
            break;
        case KIND_PUSH_CONFIG_GET:
            encoder.method("tasks/pushNotificationConfig/get").params().object(null)
                .member("id", beginEx.pushConfigGet().taskId().asString())
                .member("pushNotificationConfigId", beginEx.pushConfigGet().configId().asString()).endObject().end();
            break;
        case KIND_PUSH_CONFIG_LIST:
            encoder.method("tasks/pushNotificationConfig/list").params().object(null)
                .member("id", beginEx.pushConfigList().taskId().asString()).endObject().end();
            break;
        case KIND_PUSH_CONFIG_DELETE:
            encoder.method("tasks/pushNotificationConfig/delete").params().object(null)
                .member("id", beginEx.pushConfigDelete().taskId().asString())
                .member("pushNotificationConfigId", beginEx.pushConfigDelete().configId().asString()).endObject().end();
            break;
        case KIND_EXTENDED_AGENT_CARD:
        default:
            encoder.method("agent/getAuthenticatedExtendedCard").end();
            break;
        }

        return encoder.limit();
    }

    @Override
    public boolean translatesRequest(
        String tenant)
    {
        return true;
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
        return translator.request(kind, input, inputOffset, inputLimit, output, outputOffset);
    }

    @Override
    public boolean translatesResult()
    {
        return true;
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
        return translator.result(kind, card, input, inputOffset, inputLimit, output, outputOffset);
    }
}
