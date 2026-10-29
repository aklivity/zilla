/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoverySearchConfig;
import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aAgentCards;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aCachedAgent;
import io.aklivity.zilla.runtime.binding.a2a.internal.search.A2aAgentSearchIndexFactory;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchDocument;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex.CompletionCallback;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class A2aDiscoverySearch
{
    private static final int LIMIT_DEFAULT = 5;
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String SKILLS = "skills";
    private static final List<String> FIELDS_DEFAULT = List.of(NAME, DESCRIPTION, SKILLS);
    private static final String[] SKILL_TEXT_MEMBERS = { "id", "name", "description" };
    private static final String[] SKILL_TEXT_LISTS = { "tags", "examples" };

    private final EngineContext context;
    private final A2aAgentCards cards;
    private final A2aDiscoverySearchConfig search;
    private final A2aAgentSearchIndexFactory factory;
    private final Map<String, View> views;

    private long revision;

    public A2aDiscoverySearch(
        EngineContext context,
        A2aAgentCards cards,
        A2aDiscoverySearchConfig search)
    {
        this.context = context;
        this.cards = cards;
        this.search = search != null
            ? search
            : A2aDiscoverySearchConfig.builder()
                .limit(LIMIT_DEFAULT)
                .fields(FIELDS_DEFAULT)
                .index(A2aKeywordSearchIndexConfig.builder().build())
                .build();
        this.factory = new A2aAgentSearchIndexFactory();
        this.views = new LinkedHashMap<>();
        this.revision = -1L;
    }

    public void find(
        long authorization,
        String query,
        int requestedLimit,
        CompletionCallback<List<A2aDiscoveryMatch>> completed)
    {
        final int maximum = search.limit > 0 ? search.limit : LIMIT_DEFAULT;
        final int limit = requestedLimit > 0 ? Math.min(requestedLimit, maximum) : maximum;

        cards.ready(() -> onReady(authorization, query, limit, completed));
    }

    private void onReady(
        long authorization,
        String query,
        int limit,
        CompletionCallback<List<A2aDiscoveryMatch>> completed)
    {
        if (revision != cards.revision())
        {
            views.clear();
            revision = cards.revision();
        }

        final List<A2aCachedAgent> agents = cards.list(authorization);
        final View view = views.computeIfAbsent(viewKeyOf(agents), key -> new View(agents));

        view.query(query, limit, completed);
    }

    private static String viewKeyOf(
        List<A2aCachedAgent> agents)
    {
        final StringBuilder key = new StringBuilder();

        for (A2aCachedAgent agent : agents)
        {
            key.append(agent.agent).append('[');

            if (agent.skills != null)
            {
                agent.skills.forEach(skill -> key.append(skill.asJsonObject().getString("id", "")).append(','));
            }

            key.append(']');
        }

        return key.toString();
    }

    private static A2aAgentSearchDocument documentOf(
        A2aCachedAgent agent)
    {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(NAME, agent.name);
        fields.put(DESCRIPTION, agent.description);
        fields.put(SKILLS, skillTextOf(agent));

        return new A2aAgentSearchDocument(agent.agent, fields);
    }

    private static String skillTextOf(
        A2aCachedAgent agent)
    {
        final List<String> text = new ArrayList<>();

        if (agent.skills != null)
        {
            for (JsonValue value : agent.skills)
            {
                final JsonObject skill = value.asJsonObject();

                for (String member : SKILL_TEXT_MEMBERS)
                {
                    text.add(skill.getString(member, ""));
                }

                for (String list : SKILL_TEXT_LISTS)
                {
                    if (skill.get(list) instanceof JsonArray values)
                    {
                        values.getValuesAs(JsonString.class).forEach(item -> text.add(item.getString()));
                    }
                }
            }
        }

        return String.join(" ", text);
    }

    private final class View
    {
        private final Map<String, A2aCachedAgent> agents = new LinkedHashMap<>();
        private final Collection<A2aAgentSearchDocument> documents = new ArrayList<>();
        private final List<CompletionCallback<Void>> pending = new ArrayList<>();
        private final A2aAgentSearchIndex index;

        private boolean building;
        private boolean built;

        View(
            List<A2aCachedAgent> visible)
        {
            for (A2aCachedAgent agent : visible)
            {
                agents.put(agent.agent, agent);
                documents.add(documentOf(agent));
            }

            this.index = factory.create(context, search);
        }

        void query(
            String text,
            int limit,
            CompletionCallback<List<A2aDiscoveryMatch>> completed)
        {
            whenBuilt(new CompletionCallback<>()
            {
                @Override
                public void completed(
                    Void result)
                {
                    index.query(text, new CompletionCallback<>()
                    {
                        @Override
                        public void completed(
                            List<A2aAgentSearchMatch> matches)
                        {
                            completed.completed(matches.stream()
                                .filter(match -> agents.containsKey(match.agent))
                                .limit(limit)
                                .map(match -> new A2aDiscoveryMatch(agents.get(match.agent), match.score))
                                .toList());
                        }

                        @Override
                        public void failed(
                            Throwable ex)
                        {
                            completed.failed(ex);
                        }
                    });
                }

                @Override
                public void failed(
                    Throwable ex)
                {
                    completed.failed(ex);
                }
            });
        }

        private void whenBuilt(
            CompletionCallback<Void> callback)
        {
            if (built)
            {
                callback.completed(null);
            }
            else
            {
                pending.add(callback);

                if (!building)
                {
                    building = true;
                    index.index(documents, new CompletionCallback<>()
                    {
                        @Override
                        public void completed(
                            Void result)
                        {
                            built = true;
                            settle(callback -> callback.completed(null));
                        }

                        @Override
                        public void failed(
                            Throwable ex)
                        {
                            views.values().remove(View.this);
                            settle(callback -> callback.failed(ex));
                        }
                    });
                }
            }
        }

        private void settle(
            Consumer<CompletionCallback<Void>> outcome)
        {
            final List<CompletionCallback<Void>> settled = new ArrayList<>(pending);
            pending.clear();
            settled.forEach(outcome);
        }
    }
}
