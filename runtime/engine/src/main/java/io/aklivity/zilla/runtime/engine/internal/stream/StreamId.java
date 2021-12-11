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
package io.aklivity.zilla.runtime.engine.internal.stream;

public final class StreamId
{
    public static int streamIndex(
        long streamId)
    {
        return isInitial(streamId) ? localIndex(streamId) : remoteIndex(streamId);
    }

    public static int throttleIndex(
        long streamId)
    {
        return isInitial(streamId) ? remoteIndex(streamId) : localIndex(streamId);
    }

    public static int localIndex(
        long streamId)
    {
        return (int)(streamId >> 56) & 0x7f;
    }

    public static int remoteIndex(
        long streamId)
    {
        return (int)(streamId >> 48) & 0x7f;
    }

    public static int instanceId(
        long streamId)
    {
        return (int)(streamId & 0xffff_ffffL);
    }

    public static long streamId(
        int localIndex,
        int remoteIndex,
        int instanceId)
    {
        return isInitial(instanceId) ? ((localIndex & 0x7fL) << 56) | ((remoteIndex & 0x7fL) << 48) | instanceId
                : ((remoteIndex & 0x7fL) << 56) | ((localIndex & 0x7fL) << 48) | instanceId;
    }

    public static long throttleId(
        int localIndex,
        int remoteIndex,
        int instanceId)
    {
        return isInitial(instanceId) ? ((remoteIndex & 0x7fL) << 56) | ((localIndex & 0x7fL) << 48) | instanceId
                : ((localIndex & 0x7fL) << 56) | ((remoteIndex & 0x7fL) << 48) | instanceId;
    }

    public static boolean isInitial(
        long streamId)
    {
        return (streamId & 0x0000_0000_0000_0001L) != 0L;
    }

    private static boolean isInitial(
        int instanceId)
    {
        return (instanceId & 0x0000_0001) != 0;
    }

    private StreamId()
    {
    }
}
