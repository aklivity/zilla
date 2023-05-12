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
package io.aklivity.zilla.runtime.engine.internal.registry;

import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

final class BindingRegistry
{
    private final BindingConfig binding;
    private final BindingContext context;

    private BindingHandler attached;
    private MessageConsumer sentOriginMetricHandler = MessageConsumer.NOOP;
    private MessageConsumer receivedOriginMetricHandler = MessageConsumer.NOOP;
    private MessageConsumer sentRoutedMetricHandler = MessageConsumer.NOOP;
    private MessageConsumer receivedRoutedMetricHandler = MessageConsumer.NOOP;

    BindingRegistry(
        BindingConfig binding,
        BindingContext context)
    {
        this.binding = binding;
        this.context = context;
    }

    public void attach()
    {
        attached = context.attach(binding);
    }

    public void detach()
    {
        context.detach(binding);
        attached = null;
    }

    public BindingHandler streamFactory()
    {
        return attached;
    }

    public void sentOriginMetricHandler(
        MessageConsumer sentOriginMetricHandler)
    {
        this.sentOriginMetricHandler = sentOriginMetricHandler;
    }

    public void receivedOriginMetricHandler(
        MessageConsumer receivedOriginMetricHandler)
    {
        this.receivedOriginMetricHandler = receivedOriginMetricHandler;
    }

    public void sentRoutedMetricHandler(
        MessageConsumer sentRoutedMetricHandler)
    {
        this.sentRoutedMetricHandler = sentRoutedMetricHandler;
    }

    public void receivedRoutedMetricHandler(
        MessageConsumer receivedRoutedMetricHandler)
    {
        this.receivedRoutedMetricHandler = receivedRoutedMetricHandler;
    }

    public MessageConsumer sentOriginMetricHandler()
    {
        return sentOriginMetricHandler;
    }

    public MessageConsumer receivedOriginMetricHandler()
    {
        return receivedOriginMetricHandler;
    }

    public MessageConsumer sentRoutedMetricHandler()
    {
        return sentRoutedMetricHandler;
    }

    public MessageConsumer receivedRoutedMetricHandler()
    {
        return receivedRoutedMetricHandler;
    }
}
