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
package io.aklivity.zilla.runtime.engine;

import java.net.InetAddress;
import java.net.URL;
import java.nio.channels.SelectableChannel;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public interface EngineContext
{
    int index();

    Signaler signaler();

    int supplyTypeId(
        String name);

    long supplyInitialId(
        long bindingId);

    long supplyReplyId(
        long initialId);

    long supplyPromiseId(
            long initialId);

    long supplyAuthorizedId();

    long supplyBudgetId();

    long supplyTraceId();

    MessageConsumer supplySender(
        long streamId);

    MessageConsumer supplyReceiver(
        long streamId);

    void detachSender(
        long replyId);

    void detachStreams(
        long bindingId);

    BudgetCreditor creditor();

    BudgetDebitor supplyDebitor(
        long budgetId);

    MutableDirectBuffer writeBuffer();

    BufferPool bufferPool();

    MessageConsumer droppedFrameHandler();

    int supplyClientIndex(
        long streamId);

    InetAddress[] resolveHost(
        String host);

    PollerKey supplyPollerKey(
        SelectableChannel channel);

    long supplyBindingId(
        NamespaceConfig namespace,
        BindingConfig binding);

    String supplyNamespace(
        long bindingId);

    String supplyLocalName(
        long bindingId);

    BindingHandler streamFactory();

    GuardHandler supplyGuard(
        long guardId);

    VaultHandler supplyVault(
        long vaultId);

    URL resolvePath(
        String path);

    Metric resolveMetric(
        String name);

    void onExporterAttached(
        long exporterId);

    void onExporterDetached(
        long exporterId);
}
