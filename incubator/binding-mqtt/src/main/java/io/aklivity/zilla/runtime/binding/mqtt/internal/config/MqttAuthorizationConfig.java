package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import java.util.List;
import java.util.function.Function;

public final class MqttAuthorizationConfig
{
    public static final Function<Function<String, String>, String> DEFAULT_CREDENTIALS = f -> null;

    public final String name;
    public final MqttCredentialsConfig credentials;

    public MqttAuthorizationConfig(
        String name,
        MqttCredentialsConfig credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }

    public static final class MqttCredentialsConfig
    {
        public final List<MqttPatternConfig> connect;

        public MqttCredentialsConfig(
            List<MqttPatternConfig> connect)
        {
            this.connect = connect;
        }
    }

    public static final class MqttPatternConfig
    {
        public final String name;
        public final String pattern;

        public MqttPatternConfig(
            String name,
            String pattern)
        {
            this.name = name;
            this.pattern = pattern;
        }
    }
}

