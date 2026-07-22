package org.example.infrastructure.http;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.example.utils.RequestAuthHeaders;

import java.util.Map;

/**
 * Autenticação via cookie httpOnly (BFF / same-origin na borda).
 *
 * <p>Quando o front chama a API sem headers de autorização (por exemplo através de
 * um proxy same-origin ou de um rewrite de borda que encaminha o cookie), este filtro
 * copia o JWT do cookie {@code auth-token} para o header {@value RequestAuthHeaders#BAGGAGI_AUTHORIZATION}.
 * Assim toda a lógica de autenticação existente (baseada em header) continua funcionando
 * sem alterar nenhum controller.
 *
 * <p>Precedência: só atua quando NENHUM header {@code Authorization} /
 * {@value RequestAuthHeaders#BAGGAGI_AUTHORIZATION} do tipo Bearer está presente.
 * O cookie guarda apenas o JWT cru (sem o prefixo {@code Bearer }).
 */
@Slf4j
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class CookieAuthHeaderFilter implements ContainerRequestFilter {

    public static final String AUTH_COOKIE_NAME = "auth-token";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String existing =
                RequestAuthHeaders.resolveBearerHeaderLine(
                        requestContext.getHeaderString(HttpHeaders.AUTHORIZATION),
                        requestContext.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION));
        if (existing != null) {
            return;
        }

        Map<String, Cookie> cookies = requestContext.getCookies();
        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        Cookie cookie = cookies.get(AUTH_COOKIE_NAME);
        if (cookie == null) {
            return;
        }

        String token = cookie.getValue();
        if (token == null || token.isBlank()) {
            return;
        }

        token = token.trim();
        String value = token.startsWith("Bearer ") ? token : "Bearer " + token;
        requestContext.getHeaders().putSingle(RequestAuthHeaders.BAGGAGI_AUTHORIZATION, value);
    }
}
