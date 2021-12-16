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
package io.aklivity.zilla.runtime.engine.cog;

import java.net.InetAddress;
import java.net.URL;
import java.nio.channels.SelectableChannel;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.engine.cog.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.cog.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.cog.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.cog.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.cog.vault.BindingVault;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Namespace;

public interface AxleContext
{
    int index();

    Signaler signaler();

    int supplyTypeId(
        String name);

    long supplyInitialId(
        long routeId);

    long supplyReplyId(
        long initialId);

    long supplyBudgetId();

    long supplyTraceId();

    MessageConsumer supplyReceiver(
        long streamId);

    void detachSender(
        long replyId);

    BudgetCreditor creditor();

    BudgetDebitor supplyDebitor(
        long budgetId);

    MutableDirectBuffer writeBuffer();

    BufferPool bufferPool();

    LongSupplier supplyCounter(
        String name);

    LongConsumer supplyAccumulator(
        String name);

    MessageConsumer droppedFrameHandler();

    int supplyRemoteIndex(
        long streamId);

    InetAddress[] resolveHost(
        String host);

    PollerKey supplyPollerKey(
        SelectableChannel channel);

    long supplyRouteId(
        Namespace namespace,
        Binding binding);

    String supplyNamespace(
        long routeId);

    String supplyLocalName(
        long routeId);

    StreamFactory streamFactory();

    BindingVault supplyVault(
        long vaultId);

    URL resolvePath(
        String path);

    void initialOpened(
        long bindingId);

    void initialClosed(
        long bindingId);

    void initialErrored(
        long bindingId);

    void initialBytes(
        long bindingId,
        long bytes);

    void replyOpened(
        long bindingId);

    void replyClosed(
        long bindingId);

    void replyErrored(
        long bindingId);

    void replyBytes(
        long bindingId,
        long count);
}
