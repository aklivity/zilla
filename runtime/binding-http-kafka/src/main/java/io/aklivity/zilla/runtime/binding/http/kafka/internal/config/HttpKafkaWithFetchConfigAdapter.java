/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class HttpKafkaWithFetchConfigAdapter implements JsonbAdapter<HttpKafkaWithConfig, JsonObject>
{
    private static final String CAPABILITY_NAME = "capability";
    private static final String TOPIC_NAME = "topic";
    private static final String FILTERS_NAME = "filters";
    private static final String FILTERS_KEY_NAME = "key";
    private static final String FILTERS_HEADERS_NAME = "headers";
    private static final String MERGE_NAME = "merge";
    private static final String MERGE_CONTENT_TYPE_NAME = "content-type";
    private static final String MERGE_CONTENT_TYPE_VALUE_APPLICATION_JSON = "application/json";
    private static final String MERGE_PATCH_NAME = "patch";
    private static final String MERGE_PATCH_INITIAL_NAME = "initial";
    private static final String MERGE_PATCH_INITIAL_DEFAULT = "[]";
    private static final String MERGE_PATCH_PATH_NAME = "path";
    private static final String MERGE_PATCH_PATH_DEFAULT = "/-";

    @Override
    public JsonObject adaptToJson(
        HttpKafkaWithConfig with)
    {
        HttpKafkaWithFetchConfig fetch = with.fetch.get();

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(CAPABILITY_NAME, HttpKafkaCapability.FETCH.asString());

        object.add(TOPIC_NAME, fetch.topic);

        if (fetch.filters.isPresent())
        {
            JsonArrayBuilder newFilters = Json.createArrayBuilder();

            for (HttpKafkaWithFetchFilterConfig filter : fetch.filters.get())
            {
                JsonObjectBuilder newFilter = Json.createObjectBuilder();

                if (filter.key.isPresent())
                {
                    newFilter.add(FILTERS_KEY_NAME, filter.key.get());
                }

                if (filter.headers.isPresent())
                {
                    JsonObjectBuilder newHeaders = Json.createObjectBuilder();

                    for (HttpKafkaWithFetchFilterHeaderConfig header : filter.headers.get())
                    {
                        newHeaders.add(header.name, header.value);
                    }

                    newFilter.add(FILTERS_HEADERS_NAME, newHeaders);
                }

                newFilters.add(newFilter);
            }

            object.add(FILTERS_NAME, newFilters);
        }

        if (fetch.merge.isPresent())
        {
            final HttpKafkaWithFetchMergeConfig merge = fetch.merge.get();

            JsonObjectBuilder newMerge = Json.createObjectBuilder();

            newMerge.add(MERGE_CONTENT_TYPE_NAME, merge.contentType);

            if (!MERGE_PATCH_INITIAL_DEFAULT.equals(merge.initial) ||
                !MERGE_PATCH_PATH_DEFAULT.equals(merge.path))
            {
                JsonObjectBuilder newPatch = Json.createObjectBuilder();
                newPatch.add(MERGE_PATCH_INITIAL_NAME, merge.initial);
                newPatch.add(MERGE_PATCH_PATH_NAME, merge.path);

                newMerge.add(MERGE_PATCH_NAME, newPatch);
            }

            object.add(MERGE_NAME, newMerge);
        }

        return object.build();
    }

    @Override
    public HttpKafkaWithConfig adaptFromJson(
        JsonObject object)
    {
        String newTopic = object.getString(TOPIC_NAME);

        List<HttpKafkaWithFetchFilterConfig> newFilters = null;
        if (object.containsKey(FILTERS_NAME))
        {
            JsonArray filters = object.getJsonArray(FILTERS_NAME);
            newFilters = new ArrayList<>(filters.size());

            for (int i = 0; i < filters.size(); i++)
            {
                JsonObject filter = filters.getJsonObject(i);

                String newKey = null;
                if (filter.containsKey(FILTERS_KEY_NAME))
                {
                    newKey = filter.getString(FILTERS_KEY_NAME);
                }

                List<HttpKafkaWithFetchFilterHeaderConfig> newHeaders = null;
                if (filter.containsKey(FILTERS_HEADERS_NAME))
                {
                    JsonObject headers = filter.getJsonObject(FILTERS_HEADERS_NAME);
                    newHeaders = new ArrayList<>(headers.size());

                    for (String newHeaderName : headers.keySet())
                    {
                        String newHeaderValue = headers.getString(newHeaderName);
                        newHeaders.add(new HttpKafkaWithFetchFilterHeaderConfig(newHeaderName, newHeaderValue));
                    }
                }

                newFilters.add(new HttpKafkaWithFetchFilterConfig(newKey, newHeaders));
            }
        }

        HttpKafkaWithFetchMergeConfig newMerged = null;
        if (object.containsKey(MERGE_NAME))
        {
            JsonObject merge = object.getJsonObject(MERGE_NAME);

            String contentType = merge.getString(MERGE_CONTENT_TYPE_NAME);

            if (MERGE_CONTENT_TYPE_VALUE_APPLICATION_JSON.equals(contentType))
            {
                String initial = MERGE_PATCH_INITIAL_DEFAULT;
                String path = MERGE_PATCH_PATH_DEFAULT;

                if (merge.containsKey(MERGE_PATCH_NAME))
                {
                    JsonObject patch = merge.getJsonObject(MERGE_PATCH_NAME);

                    initial = patch.getString(MERGE_PATCH_INITIAL_NAME);
                    path = patch.getString(MERGE_PATCH_PATH_NAME);
                }

                newMerged = new HttpKafkaWithFetchMergeConfig(contentType, initial, path);
            }
        }

        return new HttpKafkaWithConfig(new HttpKafkaWithFetchConfig(newTopic, newFilters, newMerged));
    }
}
