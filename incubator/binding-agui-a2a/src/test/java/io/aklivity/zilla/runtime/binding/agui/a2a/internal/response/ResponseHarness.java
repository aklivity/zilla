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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class ResponseHarness implements AguiFrameSink, AguiResponseHost
{
    static final int AGUI_TYPE_ID = 7;
    static final long HANDLE_EXPIRES_AT = 1_000_000L;

    record Frame(
        int flags,
        String ex,
        byte[] exBytes,
        byte[] payload)
    {
        String payloadText()
        {
            return new String(payload, UTF_8);
        }

        @Override
        public String toString()
        {
            return String.format("flags=%d ex=%s payload=%s", flags, ex, payloadText());
        }
    }

    final List<Frame> frames = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    final AguiResponseMapper mapper;
    final A2aEventDecoder decoder;

    String snapshot;
    String outcome;
    String detail;
    int errorStatus;
    String errorCode;
    String errorMessage;

    ResponseHarness(
        int maximum)
    {
        this.mapper = new AguiResponseMapper(this, this, AGUI_TYPE_ID, maximum);
        this.decoder = new A2aEventDecoder(mapper, maximum);
    }

    void event(
        String json,
        int window)
    {
        event(json, A2aExSpec.of(json), window);
    }

    void event(
        String json,
        A2aExSpec spec,
        int window)
    {
        final A2aDataExFW dataEx = spec != null ? spec.build() : null;
        final byte[] bytes = json.getBytes(UTF_8);
        final DirectBufferEx buffer = new UnsafeBufferEx(bytes);

        int progress = 0;

        do
        {
            final int limit = Math.min(progress + window, bytes.length);
            final int flags = (progress == 0 ? INIT : 0) | (limit == bytes.length ? FIN : 0);

            decoder.decode(flags, progress == 0 ? dataEx : null, buffer, progress, limit);
            progress = limit;
        }
        while (progress < bytes.length);
    }

    @Override
    public byte[] snapshot(
        String contextId)
    {
        return snapshot != null ? snapshot.getBytes(UTF_8) : null;
    }

    @Override
    public InterruptHandle interruptHandle(
        String taskId,
        String contextId)
    {
        return new InterruptHandle("h-1", HANDLE_EXPIRES_AT);
    }

    @Override
    public void message(
        int flags,
        AguiDataExFW dataEx,
        DirectBufferEx payload,
        int offset,
        int limit)
    {
        final byte[] bytes = new byte[limit - offset];
        payload.getBytes(offset, bytes);

        String ex = null;
        byte[] exBytes = new byte[0];

        if (dataEx != null)
        {
            exBytes = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), exBytes);

            switch (dataEx.kind())
            {
            case AguiDataExFW.KIND_TEXT:
                ex = String.format("text(%s,%s)", dataEx.text().messageId().asString(), dataEx.text().role().get());
                break;
            case AguiDataExFW.KIND_CUSTOM:
                ex = String.format("custom(%s)", dataEx.custom().name().asString());
                break;
            case AguiDataExFW.KIND_STATE:
                ex = String.format("state(%s)", dataEx.state().type().get());
                break;
            default:
                ex = "kind" + dataEx.kind();
                break;
            }
        }

        frames.add(new Frame(flags, ex, exBytes, bytes));
    }

    @Override
    public void runFinished(
        AguiOutcomeType outcome,
        String detail)
    {
        this.outcome = outcome.name();
        this.detail = detail;
    }

    @Override
    public void runError(
        int status,
        String code,
        String message)
    {
        this.errorStatus = status;
        this.errorCode = code;
        this.errorMessage = message;
    }

    List<String> logical()
    {
        final List<String> logical = new ArrayList<>();
        StringBuilder open = null;
        String header = null;

        for (Frame frame : frames)
        {
            if (frame.ex() != null && (frame.flags() & FIN) != 0)
            {
                logical.add(frame.ex() + "=" + frame.payloadText());
            }
            else if (frame.ex() != null)
            {
                header = frame.ex();
                open = new StringBuilder(frame.payloadText());
            }
            else if ((frame.flags() & FIN) != 0)
            {
                logical.add(header + "=" + open + frame.payloadText() + (header.startsWith("custom") ? "" : "|fin"));
                open = null;
            }
            else
            {
                open.append(frame.payloadText());
            }
        }

        return logical;
    }

    String terminal()
    {
        return outcome != null ? "end " + outcome : errorCode != null ? "error " + errorCode + "|" + errorMessage : "open";
    }
}
