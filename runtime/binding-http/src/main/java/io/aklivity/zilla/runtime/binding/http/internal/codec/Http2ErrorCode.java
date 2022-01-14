/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.codec;

public enum Http2ErrorCode
{
    NO_ERROR(0),
    PROTOCOL_ERROR(1),
    INTERNAL_ERROR(2),
    FLOW_CONTROL_ERROR(3),
    SETTINGS_TIMEOUT(4),
    STREAM_CLOSED(5),
    FRAME_SIZE_ERROR(6),
    REFUSED_STREAM(7),
    CANCEL(8),
    COMPRESSION_ERROR(9),
    CONNECT_ERROR(10),
    ENHANCE_YOUR_CALM(11),
    INADEQUATE_SECURITY(12),
    HTTP_1_1_REQUIRED(13);

    public final int errorCode;

    Http2ErrorCode(int errorCode)
    {
        this.errorCode = errorCode;
    }

    static Http2ErrorCode from(int errorCode)
    {
        switch (errorCode)
        {
        case 0 : return NO_ERROR;
        case 1 : return PROTOCOL_ERROR;
        case 2 : return INTERNAL_ERROR;
        case 3 : return FLOW_CONTROL_ERROR;
        case 4 : return SETTINGS_TIMEOUT;
        case 5 : return STREAM_CLOSED;
        case 6 : return FRAME_SIZE_ERROR;
        case 7 : return REFUSED_STREAM;
        case 8 : return CANCEL;
        case 9 : return COMPRESSION_ERROR;
        case 10 : return CONNECT_ERROR;
        case 11 : return ENHANCE_YOUR_CALM;
        case 12 : return INADEQUATE_SECURITY;
        case 13 : return HTTP_1_1_REQUIRED;
        default : return null;
        }
    }
}
