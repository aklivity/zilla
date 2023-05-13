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
package io.aklivity.zilla.specs.binding.mqtt.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/publish.one.message/client",
        "${app}/publish.one.message/server"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.multiple.messages/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.property/client",
        "${app}/publish.with.user.property/server"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.properties.repeated/client",
        "${app}/publish.with.user.properties.repeated/server"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.properties.distinct/client",
        "${app}/publish.with.user.properties.distinct/server"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.message.with.topic.alias/client",
        "${app}/publish.message.with.topic.alias/server"})
    public void shouldSendMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.distinct/client",
        "${app}/publish.messages.with.topic.alias.distinct/server"})
    public void shouldSendMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.repeated/client",
        "${app}/publish.messages.with.topic.alias.repeated/server"})
    public void shouldSendMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.replaced/client",
        "${app}/publish.messages.with.topic.alias.replaced/server"})
    public void shouldSendMessagesWithTopicAliasesReplaced() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.2-7]
    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.invalid.scope/client",
        "${app}/publish.messages.with.topic.alias.invalid.scope/server"})
    public void shouldSendMessagesWithTopicAliasInvalidScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.retained/client",
        "${app}/publish.retained/server"})
    public void shouldSendRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.empty.retained.message/client",
        "${app}/publish.empty.retained.message/server"})
    public void shouldSendEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.empty.message/client",
        "${app}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }
}
