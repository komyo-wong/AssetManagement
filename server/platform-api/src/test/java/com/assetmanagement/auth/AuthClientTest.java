package com.assetmanagement.auth;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthClientTest {

    @Test
    void loginClientDefaultsToWeb() {
        assertSame(AuthClient.WEB, AuthClient.fromLogin(null));
        assertSame(AuthClient.WEB, AuthClient.fromLogin(""));
        assertSame(AuthClient.WEB, AuthClient.fromLogin("web"));
        assertSame(AuthClient.MOBILE, AuthClient.fromLogin("mobile"));
        assertSame(AuthClient.MOBILE, AuthClient.fromLogin(" Mobile "));
        BusinessException ex = assertThrows(BusinessException.class, () -> AuthClient.fromLogin("app"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void missingJwtClaimIsWeb() {
        assertSame(AuthClient.WEB, AuthClient.fromClaim(null));
        assertSame(AuthClient.MOBILE, AuthClient.fromClaim("mobile"));
    }
}
