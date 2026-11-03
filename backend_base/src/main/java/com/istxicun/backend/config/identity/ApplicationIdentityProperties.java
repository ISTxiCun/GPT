package com.istxicun.backend.config.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.identity", ignoreUnknownFields = false)
public record ApplicationIdentityProperties(
    @DefaultValue("backend-base") String namespace, @DefaultValue("") String environment) {}
