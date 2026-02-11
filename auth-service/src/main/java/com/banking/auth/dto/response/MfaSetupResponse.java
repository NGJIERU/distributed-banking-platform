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
public class MfaSetupResponse {

    private String secret;
    private String qrCodeDataUri;
    private String message;

    public static MfaSetupResponse of(String secret, String qrCodeDataUri) {
        return MfaSetupResponse.builder()
                .secret(secret)
                .qrCodeDataUri(qrCodeDataUri)
                .message("Scan the QR code with Google Authenticator, then verify with a code")
                .build();
    }
}
