package com.xeye.backend.apikey.application.port.out;

/** Puerto de salida que genera una API key aleatoria nueva. */
public interface ApiKeyGenerator {

    String generate();
}
