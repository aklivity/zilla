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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.Closeable;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    private final MessageDigest md5;

    protected final ScheduledExecutorService executor;
    protected final BiFunction<URL, String, NamespaceConfig> changeListener;

    protected WatcherTask(
        BiFunction<URL, String, NamespaceConfig> changeListener)
    {
        this.changeListener = changeListener;
        this.md5 = initMessageDigest("MD5");
        this.executor  = Executors.newScheduledThreadPool(2);
    }

    public abstract Future<Void> submit();

    public abstract CompletableFuture<NamespaceConfig> watch(
        URL configURL);

    protected byte[] computeHash(
        String configText)
    {
        return md5.digest(configText.getBytes(UTF_8));
    }

    private MessageDigest initMessageDigest(
        String algorithm)
    {
        MessageDigest md5 = null;
        try
        {
            md5 = MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return md5;
    }
}
