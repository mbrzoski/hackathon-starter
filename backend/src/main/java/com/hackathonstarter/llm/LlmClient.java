package com.hackathonstarter.llm;

import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;

/** Single seam to the model provider. Mock it in tests; swap implementations via {@code app.llm.provider}. */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);
}
