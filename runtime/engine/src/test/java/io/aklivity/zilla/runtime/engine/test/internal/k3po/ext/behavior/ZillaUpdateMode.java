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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import java.util.Objects;

public enum ZillaUpdateMode
{
    STREAM,
    HANDSHAKE,
    PROACTIVE,
    MESSAGE,
    NONE;

    public static ZillaUpdateMode decode(
        String value)
    {
        Objects.requireNonNull(value);

        switch (value)
        {
        case "stream":
            return STREAM;
        case "handshake":
            return HANDSHAKE;
        case "proactive":
            return PROACTIVE;
        case "message":
            return MESSAGE;
        case "none":
            return NONE;
        default:
            throw new IllegalArgumentException(value);
        }
    }
}
