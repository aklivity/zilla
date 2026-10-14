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
package io.aklivity.zilla.config.classifier.presidio.internal;

import static io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfigBuilder.LANGUAGE_DEFAULT;
import static io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfigBuilder.TIMEOUT_DEFAULT;

import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;
import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PresidioClassifierOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String ENDPOINT_NAME = "endpoint";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String LANGUAGE_NAME = "language";
    private static final String THRESHOLD_NAME = "threshold";
    private static final String TIMEOUT_NAME = "timeout";
    private static final String LABELS_NAME = "labels";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        PresidioClassifierOptionsConfig presidio = (PresidioClassifierOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder()
            .add(ENDPOINT_NAME, presidio.endpoint);

        if (presidio.authorization != null)
        {
            object.add(CREDENTIALS_NAME, Json.createObjectBuilder()
                .add(AUTHORIZATION_NAME, presidio.authorization));
        }

        if (!LANGUAGE_DEFAULT.equals(presidio.language))
        {
            object.add(LANGUAGE_NAME, presidio.language);
        }

        if (presidio.threshold > 0.0)
        {
            object.add(THRESHOLD_NAME, presidio.threshold);
        }

        if (!TIMEOUT_DEFAULT.equals(presidio.timeout))
        {
            object.add(TIMEOUT_NAME, presidio.timeout.toString());
        }

        if (!presidio.labels.isEmpty())
        {
            JsonObjectBuilder labels = Json.createObjectBuilder();
            presidio.labels.forEach(labels::add);
            object.add(LABELS_NAME, labels);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        PresidioClassifierOptionsConfigBuilder<PresidioClassifierOptionsConfig> options =
            PresidioClassifierOptionsConfig.builder()
                .endpoint(object.getString(ENDPOINT_NAME));

        if (object.containsKey(CREDENTIALS_NAME))
        {
            JsonObject credentials = object.getJsonObject(CREDENTIALS_NAME);

            if (credentials.containsKey(AUTHORIZATION_NAME))
            {
                options.authorization(credentials.getString(AUTHORIZATION_NAME));
            }
        }

        if (object.containsKey(LANGUAGE_NAME))
        {
            options.language(object.getString(LANGUAGE_NAME));
        }

        if (object.containsKey(THRESHOLD_NAME))
        {
            options.threshold(object.getJsonNumber(THRESHOLD_NAME).doubleValue());
        }

        if (object.containsKey(TIMEOUT_NAME))
        {
            options.timeout(max(Duration.ZERO, Duration.parse(object.getString(TIMEOUT_NAME))));
        }

        if (object.containsKey(LABELS_NAME))
        {
            object.getJsonObject(LABELS_NAME).forEach((alias, entity) -> options.label(alias, ((JsonString) entity).getString()));
        }

        return options.build();
    }

    private static Duration max(
        Duration minimum,
        Duration duration)
    {
        return duration.compareTo(minimum) < 0 ? minimum : duration;
    }
}
