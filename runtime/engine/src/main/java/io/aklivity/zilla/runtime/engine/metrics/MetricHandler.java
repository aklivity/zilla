package io.aklivity.zilla.runtime.engine.metrics;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public interface MetricHandler
{
    MessageConsumer onReceived(
            int msgTypeId, DirectBuffer buffer, int index, int length);

    MessageConsumer onSent(
            int msgTypeId, DirectBuffer buffer, int index, int length);
}
