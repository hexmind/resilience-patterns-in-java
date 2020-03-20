package pl.ts;

import feign.Feign;
import feign.jackson.JacksonDecoder;

class ApiConfiguration {

    private final BoatsApi boatsApi;

    ApiConfiguration(String url) {
        this.boatsApi = Feign.builder()
                .decoder(new JacksonDecoder())
                .target(BoatsApi.class, url);
    }

    BoatsApi boatsApi() {
        return boatsApi;
    }
}
