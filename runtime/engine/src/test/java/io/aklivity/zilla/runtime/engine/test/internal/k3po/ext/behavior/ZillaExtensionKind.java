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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior;

import org.jboss.netty.buffer.ChannelBuffer;

public enum ZillaExtensionKind
{
    BEGIN,
    DATA,
    FLUSH,
    ABORT,
    END,
    CHALLENGE,
    RESET(false);

    private final boolean writeAligned;
    private ZillaExtensionKind()
    {
        this(true);
    }

    private ZillaExtensionKind(
        boolean writeAligned)
    {
        this.writeAligned = writeAligned;
    }

    public ChannelBuffer decodeBuffer(
        ZillaChannel channel)
    {
        return writeAligned ? channel.writeExtBuffer(this, true) : channel.readExtBuffer(this, true);
    }

    public ChannelBuffer encodeBuffer(
        ZillaChannel channel)
    {
        return writeAligned ? channel.writeExtBuffer(this, false) : channel.readExtBuffer(this, false);
    }
}
