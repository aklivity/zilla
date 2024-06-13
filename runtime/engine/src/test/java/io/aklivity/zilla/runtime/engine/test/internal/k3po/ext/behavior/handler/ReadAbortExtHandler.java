/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.handler;

import java.util.EnumSet;

import org.jboss.netty.channel.ChannelHandlerContext;

import io.aklivity.k3po.runtime.driver.internal.behavior.handler.codec.ChannelDecoder;
import io.aklivity.k3po.runtime.driver.internal.netty.channel.ReadAbortEvent;

public class ReadAbortExtHandler extends AbstractReadExtHandler
{
    public ReadAbortExtHandler(
        ChannelDecoder decoder)
    {
        super(EnumSet.of(ChannelEventKind.READ_ABORTED), decoder);
    }

    @Override
    public void inputAborted(
        ChannelHandlerContext ctx,
        ReadAbortEvent e)
    {
        doReadExtension(ctx);

        super.inputAborted(ctx, e);
    }
}
