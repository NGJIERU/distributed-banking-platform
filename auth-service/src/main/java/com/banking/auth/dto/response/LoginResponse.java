package com.banking.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private boolean mfaRequired;
    private String mfaToken;
    private AuthResponse authResponse;

    public static LoginResponse requireMfa(String mfaToken) {
        return LoginResponse.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .build();
    }

    public static LoginResponse success(AuthResponse authResponse) {
        return LoginResponse.builder()
                .mfaRequired(false)
                .authResponse(authResponse)
                .build();
    }
}
