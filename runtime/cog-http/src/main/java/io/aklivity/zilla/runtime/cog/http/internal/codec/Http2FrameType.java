/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.http.internal.codec;

public enum Http2FrameType
{
    UNKNOWN(-1),
    DATA(0),
    HEADERS(1),
    PRIORITY(2),
    RST_STREAM(3),
    SETTINGS(4),
    PUSH_PROMISE(5),
    PING(6),
    GO_AWAY(7),
    WINDOW_UPDATE(8),
    CONTINUATION(9);

    private final byte type;

    Http2FrameType(int type)
    {
        this.type = (byte) type;
    }

    public byte type()
    {
        return type;
    }

    public static Http2FrameType get(byte type)
    {
        switch (type)
        {
        case 0 : return DATA;
        case 1 : return HEADERS;
        case 2 : return PRIORITY;
        case 3 : return RST_STREAM;
        case 4 : return SETTINGS;
        case 5 : return PUSH_PROMISE;
        case 6 : return PING;
        case 7 : return GO_AWAY;
        case 8 : return WINDOW_UPDATE;
        case 9 : return CONTINUATION;
        default: return UNKNOWN;
        }
    }
}
