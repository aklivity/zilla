/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.specs.filesystem.http;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/filesystem/http/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/read.success/client",
        "${app}/read.success/server" })
    public void shouldReadString() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.success.etag.not.modified/client",
        "${app}/read.success.etag.not.modified/server" })
    public void shouldReadStringEtagNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.success.etag.modified/client",
        "${app}/read.success.etag.modified/server" })
    public void shouldReadStringEtagModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.notfound/client",
        "${app}/read.notfound/server" })
    public void shouldReadStringNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.notfound.success/client",
        "${app}/read.notfound.success/server" })
    public void shouldReadStringNotFoundSuccess() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/watch/client",
        "${app}/watch/server" })
    public void shouldWatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/watch.read/client",
        "${app}/watch.read/server" })
    public void shouldWatchRead() throws Exception
    {
        k3po.finish();
    }
}
