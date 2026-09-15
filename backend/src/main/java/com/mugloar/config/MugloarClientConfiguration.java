package com.mugloar.config;

import com.mugloar.adapter.mugloar.AdDecoder;
import com.mugloar.adapter.mugloar.MugloarProperties;
import com.mugloar.adapter.mugloar.MugloarRestClient;
import com.mugloar.application.port.MugloarApi;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the HTTP client. Base URL and timeouts come from application.yml. */
@Configuration
public class MugloarClientConfiguration {

    @Bean
    AdDecoder adDecoder() {
        return new AdDecoder();
    }

    @Bean
    RestClient mugloarRestClient(MugloarProperties properties, RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    MugloarApi mugloarApi(RestClient mugloarRestClient, AdDecoder adDecoder, MugloarProperties properties) {
        return new MugloarRestClient(mugloarRestClient, adDecoder, properties);
    }
}
