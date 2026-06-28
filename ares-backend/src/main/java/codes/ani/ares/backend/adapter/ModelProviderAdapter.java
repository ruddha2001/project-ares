package codes.ani.ares.backend.adapter;

import codes.ani.ares.backend.dto.ProviderRequest;
import codes.ani.ares.backend.dto.ProviderResponse;

public interface ModelProviderAdapter {
    boolean supports(String providerSignature);
    ProviderResponse dispatch(ProviderRequest request);
}
