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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.agrona.collections.LongObjPredicate;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttTopicParamConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class MqttConditionMatcher
{
    private static final Pattern IDENTITY_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).identity\\}");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).attributes" +
            ".([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)\\}");

    private final Matcher identityMatcher = IDENTITY_PATTERN.matcher("");
    private final Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher("");

    private final List<Matcher> sessionMatchers;
    private final List<TopicMatcher> subscribeMatchers;
    private final List<TopicMatcher> publishMatchers;

    public MqttConditionMatcher(
        Function<String, LongFunction<String>> identities,
        Function<String, LongObjectBiFunction<String, String>> attributor,
        MqttConditionConfig condition)
    {
        this.sessionMatchers = condition.sessions != null && !condition.sessions.isEmpty()
            ? asWildcardMatcher(condition.sessions.stream().map(s -> s.clientId).collect(Collectors.toList()))
            : null;
        this.subscribeMatchers = condition.subscribes != null && !condition.subscribes.isEmpty()
            ? condition.subscribes.stream().map(s -> new TopicMatcher(identities, attributor, s.topic, s.params)).toList()
            : null;
        this.publishMatchers = condition.publishes != null && !condition.publishes.isEmpty()
            ? condition.publishes.stream().map(p -> new TopicMatcher(identities, attributor, p.topic, p.params)).toList()
            : null;
    }

    public boolean matchesSession(
        String clientId)
    {
        boolean match = sessionMatchers == null;
        if (!match)
        {
            for (Matcher matcher : sessionMatchers)
            {
                match = matcher.reset(clientId).matches();
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    public boolean matchesSubscribe(
        long authorization,
        String topic)
    {
        boolean match = false;
        if (subscribeMatchers != null)
        {
            for (TopicMatcher matcher : subscribeMatchers)
            {
                match = matcher.matches(authorization, topic);
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    public boolean matchesPublish(
        long authorization,
        String topic)
    {
        boolean match = false;
        if (publishMatchers != null)
        {
            for (TopicMatcher matcher : publishMatchers)
            {
                match = matcher.matches(authorization, topic);
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    private static List<Matcher> asWildcardMatcher(
        List<String> wildcards)
    {
        List<Matcher> matchers = new ArrayList<>();
        for (String wildcard : wildcards)
        {
            String pattern = wildcard.replace(".", "\\.").replace("*", ".*");

            if (!pattern.endsWith(".*"))
            {
                pattern = pattern + "(\\?.*)?";
            }
            matchers.add(Pattern.compile(pattern).matcher(""));

        }

        return matchers;
    }

    private final class TopicMatcher
    {
        private final Matcher matchTopic;
        private final Map<String, LongObjPredicate<String>> matchParams;

        private TopicMatcher(
            Function<String, LongFunction<String>> identities,
            Function<String, LongObjectBiFunction<String, String>> attributor,
            String wildcard,
            List<MqttTopicParamConfig> params)
        {
            this.matchTopic = Pattern.compile(wildcard
                .replace(".", "\\.")
                .replace("$", "\\$")
                .replace("+", "[^/]*")
                .replace("#", ".*")
                .replaceAll("\\{([a-zA-Z_]+)\\}", "(?<$1>[^/]+)")).matcher("");
            this.matchParams = params != null
                ? params.stream().collect(Collectors.toMap(
                    p -> p.name,
                    p -> asTopicParamMatcher(identities, attributor, p.value)))
                : null;

            Collection<String> topicParams = matchTopic.namedGroups().keySet();
            topicParams.stream()
                .filter(tp -> params == null || params.stream().noneMatch(p -> p.name.equals(tp)))
                .forEach(tp -> System.out.format("Unconstrained param for MQTT topic %s: %s\n", wildcard, tp));
            if (params != null)
            {
                params.stream()
                    .filter(p -> !topicParams.contains(p.name))
                    .forEach(p -> System.out.printf("Undefined param constraint for MQTT topic %s: %s\n", wildcard, p.name));
            }
        }

        private boolean matches(
            long authorization,
            String topic)
        {
            return matchTopic.reset(topic).matches() &&
                matchParams(name ->
                {
                    try
                    {
                        return matchTopic.group(name);
                    }
                    catch (IllegalArgumentException e)
                    {
                        return null;
                    }
                }, authorization);
        }

        private boolean matchParams(
            Function<String, String> valuesByName,
            long authorization)
        {
            return matchParams == null ||
                matchParams.entrySet().stream()
                    .allMatch(e -> e.getValue().test(authorization, valuesByName.apply(e.getKey())));
        }

        private LongObjPredicate<String> asTopicParamMatcher(
            Function<String, LongFunction<String>> identities,
            Function<String, LongObjectBiFunction<String, String>> attributor,
            String value)
        {
            LongObjPredicate<String> topic;
            if (identityMatcher.reset(value).matches())
            {
                topic = asTopicParamIdentityMatcher(identities.apply(identityMatcher.group(1)));
            }
            else if (attributeMatcher.reset(value).matches())
            {
                topic = asTopicParamAttributeMatcher(attributor.apply(attributeMatcher.group(1)), attributeMatcher.group(2));
            }
            else
            {
                topic = asTopicParamValueMatcher(value);
            }
            return topic;
        }

        private static LongObjPredicate<String> asTopicParamIdentityMatcher(
            LongFunction<String> identity)
        {
            return (a, v) -> v != null && identity != null && v.equals(identity.apply(a));
        }

        private static LongObjPredicate<String> asTopicParamAttributeMatcher(
            LongObjectBiFunction<String, String> attributor,
            String name)
        {
            return (a, v) -> v != null && attributor != null && v.equals(attributor.apply(a, name));
        }

        private static LongObjPredicate<String> asTopicParamValueMatcher(
            String expected)
        {
            return (a, v) -> v != null && v.equals(expected);
        }
    }
}
