/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPublishConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttSubscribeConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;

public class MqttConditionMatcherTest
{

    @Test
    public void shouldMatchIsolatedMultiLevelWildcard()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "#",
            "#");
        assertTrue(matcher.matchesPublish("#", 1L));
        assertTrue(matcher.matchesSubscribe("#", 1L));
        assertTrue(matcher.matchesPublish("topic", 1L));
        assertTrue(matcher.matchesSubscribe("topic", 1L));
        assertTrue(matcher.matchesPublish("topic/pub", 1L));
        assertTrue(matcher.matchesSubscribe("topic/sub", 1L));
        assertTrue(matcher.matchesPublish("topic/+/pub", 1L));
        assertTrue(matcher.matchesSubscribe("topic/+/sub", 1L));
        assertTrue(matcher.matchesPublish("topic/pub/#", 1L));
        assertTrue(matcher.matchesSubscribe("topic/sub/#", 1L));
    }

    @Test
    public void shouldMatchMultipleTopicNames()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "topic/pub",
            "topic/sub");
        assertTrue(matcher.matchesPublish("topic/pub", 1L));
        assertTrue(matcher.matchesSubscribe("topic/sub", 1L));
        assertFalse(matcher.matchesPublish("topic/#", 1L));
        assertFalse(matcher.matchesSubscribe("topic/#", 1L));
        assertFalse(matcher.matchesPublish("topic/+", 1L));
        assertFalse(matcher.matchesSubscribe("topic/+", 1L));
        assertFalse(matcher.matchesPublish("topic/sub", 1L));
        assertFalse(matcher.matchesSubscribe("topic/pub", 1L));
        assertFalse(matcher.matchesPublish("topic/pu", 1L));
        assertFalse(matcher.matchesSubscribe("topic/su", 1L));
        assertFalse(matcher.matchesPublish("topic/put", 1L));
        assertFalse(matcher.matchesSubscribe("topic/sup", 1L));
        assertFalse(matcher.matchesPublish("topic/publ", 1L));
        assertFalse(matcher.matchesSubscribe("topic/subs", 1L));
        assertFalse(matcher.matchesPublish("topicpub", 1L));
        assertFalse(matcher.matchesSubscribe("topicsub", 1L));
        assertFalse(matcher.matchesPublish("opic/pub", 1L));
        assertFalse(matcher.matchesSubscribe("opic/sub", 1L));
        assertFalse(matcher.matchesPublish("popic/pub", 1L));
        assertFalse(matcher.matchesSubscribe("zopic/sub", 1L));
    }

    @Test
    public void shouldMatchMultipleTopicNamesWithSingleLevelWildcard()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "topic/pub/+",
            "topic/sub/+");
        assertTrue(matcher.matchesPublish("topic/pub/aa", 1L));
        assertTrue(matcher.matchesSubscribe("topic/sub/bbb", 1L));
        assertTrue(matcher.matchesPublish("topic/pub/+", 1L));
        assertTrue(matcher.matchesSubscribe("topic/sub/+", 1L));
        assertFalse(matcher.matchesPublish("topic/sub/aa", 1L));
        assertFalse(matcher.matchesSubscribe("topic/pub/bbb", 1L));
        assertFalse(matcher.matchesPublish("topic/pub/aa/one", 1L));
        assertFalse(matcher.matchesSubscribe("topic/sub/bbb/two", 1L));
    }

    @Test
    public void shouldMatchMultipleTopicNamesWithSingleAndMultiLevelWildcard()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "topic/+/pub/#",
            "topic/+/sub/#");
        assertTrue(matcher.matchesPublish("topic/x/pub/aa", 1L));
        assertTrue(matcher.matchesSubscribe("topic/y/sub/b", 1L));
        assertTrue(matcher.matchesPublish("topic/x/pub/test/cc", 1L));
        assertTrue(matcher.matchesSubscribe("topic/y/sub/test/bb", 1L));
        assertFalse(matcher.matchesPublish("topic/pub/aa", 1L));
        assertFalse(matcher.matchesSubscribe("topic/sub/b", 1L));
    }

    @Test
    public void shouldMatchTopicNameWithIdentityPlaceholder()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "pub/{guarded[gname].identity}",
            "sub/{guarded[gname].identity}",
            "gname",
            Map.of(
                1L, "myuser",
                2L, "otheruser"));
        assertTrue(matcher.matchesPublish("pub/myuser", 1L));
        assertTrue(matcher.matchesSubscribe("sub/myuser", 1L));
        assertTrue(matcher.matchesPublish("pub/otheruser", 2L));
        assertTrue(matcher.matchesSubscribe("sub/otheruser", 2L));
        assertFalse(matcher.matchesPublish("pub/myuser", 2L));
        assertFalse(matcher.matchesSubscribe("sub/myuser", 2L));
        assertFalse(matcher.matchesPublish("pub/otheruser", 1L));
        assertFalse(matcher.matchesSubscribe("sub/otheruser", 1L));
        assertFalse(matcher.matchesPublish("pub/myuset", 1L));
        assertFalse(matcher.matchesSubscribe("sub/myuset", 1L));
        assertFalse(matcher.matchesPublish("pub/myusert", 1L));
        assertFalse(matcher.matchesSubscribe("sub/myusert", 1L));
        assertFalse(matcher.matchesPublish("pub/myuser/a", 1L));
        assertFalse(matcher.matchesSubscribe("sub/myuser/a", 1L));
        assertFalse(matcher.matchesPublish("pub/myuser", 3L));
        assertFalse(matcher.matchesSubscribe("sub/myuser", 3L));
        assertFalse(matcher.matchesPublish("pub/null", 3L));
        assertFalse(matcher.matchesSubscribe("sub/null", 3L));
    }

    @Test
    public void shouldMatchTopicNameWithIdentityPlaceholderAndMultiLevelWildcard()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "pub/{guarded[gname].identity}/#",
            "sub/{guarded[gname].identity}/#",
            "gname",
            Map.of(
                1L, "myuser",
                2L, "otheruser"));
        assertTrue(matcher.matchesPublish("pub/myuser/pubtest", 1L));
        assertTrue(matcher.matchesSubscribe("sub/myuser/subtest", 1L));
        assertTrue(matcher.matchesPublish("pub/myuser/pubtest/aaa", 1L));
        assertTrue(matcher.matchesSubscribe("sub/myuser/subtest/aa", 1L));
        assertTrue(matcher.matchesPublish("pub/otheruser/pubtest", 2L));
        assertTrue(matcher.matchesSubscribe("sub/otheruser/subtest", 2L));
        assertTrue(matcher.matchesPublish("pub/otheruser/pubtest/aa", 2L));
        assertTrue(matcher.matchesSubscribe("sub/otheruser/subtest/aa", 2L));
        assertFalse(matcher.matchesPublish("pub/myuser/pubtest", 2L));
        assertFalse(matcher.matchesSubscribe("sub/myuser/subtest", 2L));
        assertFalse(matcher.matchesPublish("pub/myuser/pubtest/aaa", 2L));
        assertFalse(matcher.matchesSubscribe("sub/myuser/subtest/aa", 2L));
        assertFalse(matcher.matchesPublish("pub/otheruser/pubtest", 1L));
        assertFalse(matcher.matchesSubscribe("sub/otheruser/subtest", 1L));
        assertFalse(matcher.matchesPublish("pub/otheruser/pubtest/aa", 1L));
        assertFalse(matcher.matchesSubscribe("sub/otheruser/subtest/aa", 1L));
    }

    @Test
    public void shouldNotMatchTopicNameWithInvalidIdentityPlaceholder()
    {
        MqttConditionMatcher matcher = buildMatcher(
            "pub/{guarded[invalid].identity}",
            "sub/{guarded[invalid].identity}",
            "gname",
            Map.of(
                1L, "myuser",
                2L, "otheruser"));
        assertTrue(matcher.matchesPublish("pub/{guarded[invalid].identity}", 1L));
        assertTrue(matcher.matchesSubscribe("sub/{guarded[invalid].identity}", 1L));
        assertFalse(matcher.matchesPublish("pub/myuser", 1L));
        assertFalse(matcher.matchesSubscribe("sub/myuser", 1L));
        assertFalse(matcher.matchesPublish("pub/otheruser", 2L));
        assertFalse(matcher.matchesSubscribe("sub/otheruser", 2L));
    }

    private static MqttConditionMatcher buildMatcher(
        String publishTopic,
        String subscribeTopic)
    {
        return buildMatcher(publishTopic, subscribeTopic, "", Map.of());
    }

    private static MqttConditionMatcher buildMatcher(
        String publishTopic,
        String subscribeTopic,
        String guardName,
        Map<Long, String> identities)
    {
        MqttConditionConfig condition = MqttConditionConfig.builder()
            .publish(MqttPublishConfig.builder().topic(publishTopic).build())
            .subscribe(MqttSubscribeConfig.builder().topic(subscribeTopic).build())
            .build();
        GuardedConfig guarded = GuardedConfig.builder()
            .name(guardName)
            .build();
        guarded.identity = identities::get;
        return new MqttConditionMatcher(condition, List.of(guarded));
    }
}
