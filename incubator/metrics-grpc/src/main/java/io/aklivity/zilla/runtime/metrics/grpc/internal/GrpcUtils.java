/*
 * Copyright 2021-2022 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.metrics.grpc.internal;

final class GrpcUtils
{
    private GrpcUtils()
    {
    }

    public static long initialId(
        long streamId)
    {
        // reduce both initial and reply stream ids to the same initial id
        return streamId & ~0b01L;
    }

    public static long direction(
        long streamId)
    {
        // get stream direction (1: received; 0: sent)
        return streamId & 0b01L;
    }
}
