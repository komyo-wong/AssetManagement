package com.assetmanagement.auth;

import com.assetmanagement.security.TraceIdFilter;
import com.assetmanagement.shared.api.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthMeService authMeService;
    private final RefreshCookieService refreshCookieService;
    private final ForgotPasswordService forgotPasswordService;

    public AuthController(
            AuthService authService,
            AuthMeService authMeService,
            RefreshCookieService refreshCookieService,
            ForgotPasswordService forgotPasswordService
    ) {
        this.authService = authService;
        this.authMeService = authMeService;
        this.refreshCookieService = refreshCookieService;
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        noStore(response);
        AuthClient client = AuthClient.fromLogin(loginRequest.client());
        AuthService.AuthResult result = authService.login(
                loginRequest.account(),
                loginRequest.password(),
                client,
                AuthRequestMetadata.from(request)
        );
        refreshCookieService.set(response, result.refreshToken().token());
        return LoginResponse.from(result, client.isMobile());
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        noStore(response);
        forgotPasswordService.resetAndEmail(requestBody.account(), AuthRequestMetadata.from(request));
        return ApiResponse.success(TraceIdFilter.traceId(request));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @RequestBody(required = false) TokenBody body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        noStore(response);
        String refreshToken = presentedRefreshToken(body, request, true);
        AuthService.AuthResult result = authService.refresh(
                refreshToken,
                AuthRequestMetadata.from(request)
        );
        refreshCookieService.set(response, result.refreshToken().token());
        boolean mobile = result.refreshToken().session().client().isMobile();
        return LoginResponse.from(result, mobile);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) TokenBody body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        noStore(response);
        String refreshToken = presentedRefreshToken(body, request, false);
        refreshCookieService.clear(response);
        if (refreshToken != null) {
            authService.logout(refreshToken, AuthRequestMetadata.from(request));
        }
        return ApiResponse.success(TraceIdFilter.traceId(request));
    }

    @GetMapping("/me")
    public AuthMeService.MeResponse me(HttpServletResponse response) {
        noStore(response);
        return authMeService.me();
    }

    private String presentedRefreshToken(TokenBody body, HttpServletRequest request, boolean required) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken().trim();
        }
        if (required) {
            return refreshCookieService.require(request);
        }
        return refreshCookieService.find(request);
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(
            @NotBlank @Size(max = 254) String account,
            @NotBlank @Size(max = 512) String password,
            @Size(max = 16) String client
    ) {
        public LoginRequest(String account, String password) {
            this(account, password, null);
        }
    }

    public record ForgotPasswordRequest(
            @NotBlank @Size(max = 254) String account
    ) {
    }

    public record TokenBody(@Size(max = 256) String refreshToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String accessToken, long expiresInSeconds, String refreshToken) {
        static LoginResponse from(AuthService.AuthResult result, boolean includeRefreshToken) {
            return new LoginResponse(
                    result.accessToken().token(),
                    result.accessToken().expiresInSeconds(),
                    includeRefreshToken ? result.refreshToken().token() : null
            );
        }
    }
}
