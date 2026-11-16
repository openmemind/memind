package com.openmemind.ai.memory.server.service;

import java.io.InputStream;

public interface ResourceStorageService {
    void uploadResource(String key, InputStream data, long contentLength);
}
