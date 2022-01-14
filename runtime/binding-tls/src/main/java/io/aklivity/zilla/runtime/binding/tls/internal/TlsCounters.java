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
package io.aklivity.zilla.runtime.binding.tls.internal;

import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public class TlsCounters
{
    public final LongSupplier serverDecodeNoClientHello;
    public final LongSupplier serverDecodeAcquires;
    public final LongSupplier serverDecodeReleases;
    public final LongSupplier serverEncodeAcquires;
    public final LongSupplier serverEncodeReleases;
    public final LongSupplier clientDecodeAcquires;
    public final LongSupplier clientDecodeReleases;
    public final LongSupplier clientEncodeAcquires;
    public final LongSupplier clientEncodeReleases;

    public TlsCounters(
        Function<String, LongSupplier> supplyCounter,
        Function<String, LongConsumer> supplyAccumulator)
    {
        this.serverDecodeNoClientHello = supplyCounter.apply("tls.server.decode.no.client.hello");
        this.serverDecodeAcquires = supplyCounter.apply("tls.server.decode.acquires");
        this.serverDecodeReleases = supplyCounter.apply("tls.server.decode.releases");
        this.serverEncodeAcquires = supplyCounter.apply("tls.server.encode.acquires");
        this.serverEncodeReleases = supplyCounter.apply("tls.server.encode.releases");
        this.clientDecodeAcquires = supplyCounter.apply("tls.client.decode.acquires");
        this.clientDecodeReleases = supplyCounter.apply("tls.client.decode.releases");
        this.clientEncodeAcquires = supplyCounter.apply("tls.client.encode.acquires");
        this.clientEncodeReleases = supplyCounter.apply("tls.client.encode.releases");
    }
}
