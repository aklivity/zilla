package io.aklivity.zilla.runtime.engine.metrics;

import org.agrona.DirectBuffer;

public interface MetricHandler
{
    void onReceived(
        int msgTypeId, DirectBuffer buffer, int index, int length);

    void onSent(
        int msgTypeId, DirectBuffer buffer, int index, int length);
}
