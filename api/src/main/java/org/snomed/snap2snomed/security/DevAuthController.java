/*
 * Copyright © 2026 SNOMED International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.snomed.snap2snomed.security;

import static org.snomed.snap2snomed.security.DevAuthConfiguration.TOKEN_USE_ACCESS;
import static org.snomed.snap2snomed.security.DevAuthConfiguration.TOKEN_USE_CLAIM;
import static org.snomed.snap2snomed.security.DevAuthConfiguration.TOKEN_USE_CODE;
import static org.snomed.snap2snomed.security.DevAuthConfiguration.TOKEN_USE_ID;
import static org.snomed.snap2snomed.security.DevAuthConfiguration.TOKEN_USE_REFRESH;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.snomed.snap2snomed.config.Snap2snomedConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * LOCAL DEVELOPMENT ONLY. A fake, Cognito-compatible identity provider used by the UI when the
 * "devauth" profile is active. See {@link DevAuthConfiguration}.
 */
@Hidden
@RestController
@Profile(DevAuthConfiguration.PROFILE)
@RequestMapping(DevAuthConfiguration.BASE_PATH)
public class DevAuthController {

  private static final Duration CODE_TTL = Duration.ofMinutes(5);
  private static final Duration TOKEN_TTL = Duration.ofHours(1);
  private static final Duration REFRESH_TTL = Duration.ofDays(30);

  private final RSASSASigner signer;
  private final JwtDecoder accessTokenDecoder;
  private final JwtDecoder grantDecoder;
  private final Snap2snomedConfiguration config;

  @Value("${snap2snomed.devauth.defaultEmail:dev@example.com}")
  private String defaultEmail;

  @Value("${snap2snomed.devauth.defaultGivenName:Dev}")
  private String defaultGivenName;

  @Value("${snap2snomed.devauth.defaultFamilyName:User}")
  private String defaultFamilyName;

  public DevAuthController(KeyPair devAuthKeyPair,
      @Qualifier("jwtDecoder") JwtDecoder accessTokenDecoder,
      @Qualifier("devAuthGrantDecoder") JwtDecoder grantDecoder,
      Snap2snomedConfiguration config) {
    this.signer = new RSASSASigner(devAuthKeyPair.getPrivate());
    this.accessTokenDecoder = accessTokenDecoder;
    this.grantDecoder = grantDecoder;
    this.config = config;
  }

  /**
   * Equivalent of the Cognito hosted UI: shows a form to pick the identity to log in as.
   * {@code /authorize} is what the UI calls when an identity provider is configured.
   */
  @GetMapping(value = {"/login", "/authorize"}, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> loginForm(
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam(value = "state", required = false) String state) {
    requireLocalUri(redirectUri);
    String html = """
        <!doctype html>
        <html><head><meta charset="utf-8"><title>Snap2SNOMED dev login</title>
        <style>
          body{font-family:system-ui,sans-serif;max-width:420px;margin:48px auto;padding:0 16px}
          label{display:block;margin-top:12px;font-size:14px}
          input[type=text],input[type=email]{width:100%%;padding:6px;box-sizing:border-box}
          .warn{background:#fff3cd;border:1px solid #e0c36c;padding:8px;font-size:13px}
          button{margin-top:18px;padding:8px 16px}
        </style></head><body>
        <h2>Snap2SNOMED &ndash; dev login</h2>
        <p class="warn">Fake identity provider (<code>devauth</code> profile). Local use only.
        The same email always maps to the same user.</p>
        <form method="post" action="login">
          <input type="hidden" name="redirect_uri" value="%s">
          <input type="hidden" name="state" value="%s">
          <label>Email <input type="email" name="email" value="%s" required></label>
          <label>Given name <input type="text" name="given_name" value="%s" required></label>
          <label>Family name <input type="text" name="family_name" value="%s" required></label>
          <label><input type="checkbox" name="admin" value="true"> Admin (%s)</label>
          <button type="submit">Log in</button>
        </form></body></html>
        """.formatted(
        HtmlUtils.htmlEscape(redirectUri),
        HtmlUtils.htmlEscape(state == null ? "" : state),
        HtmlUtils.htmlEscape(defaultEmail),
        HtmlUtils.htmlEscape(defaultGivenName),
        HtmlUtils.htmlEscape(defaultFamilyName),
        HtmlUtils.htmlEscape(config.getSecurity().getAdminGroup()));
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> login(
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam("email") String email,
      @RequestParam("given_name") String givenName,
      @RequestParam("family_name") String familyName,
      @RequestParam(value = "admin", defaultValue = "false") boolean admin) {
    requireLocalUri(redirectUri);
    if (!StringUtils.hasText(email)) {
      return ResponseEntity.badRequest().build();
    }
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("sub", subjectFor(email));
    identity.put("email", email.trim());
    identity.put("given_name", givenName.trim());
    identity.put("family_name", familyName.trim());
    identity.put("cognito:groups",
        admin ? List.of(config.getSecurity().getAdminGroup()) : List.<String>of());

    String code = sign(identity, TOKEN_USE_CODE, CODE_TTL);
    UriComponentsBuilder target = UriComponentsBuilder.fromUriString(redirectUri)
        .queryParam("code", code);
    if (StringUtils.hasText(state)) {
      target.queryParam("state", state);
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(target.encode(StandardCharsets.UTF_8).build().toUri()).build();
  }

  @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> token(
      @RequestParam("grant_type") String grantType,
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "refresh_token", required = false) String refreshToken) {
    String expectedUse;
    String grant;
    switch (grantType) {
      case "authorization_code" -> {
        expectedUse = TOKEN_USE_CODE;
        grant = code;
      }
      case "refresh_token" -> {
        expectedUse = TOKEN_USE_REFRESH;
        grant = refreshToken;
      }
      default -> {
        return oauthError("unsupported_grant_type");
      }
    }

    Jwt decoded;
    try {
      decoded = grantDecoder.decode(grant);
    } catch (JwtException | IllegalArgumentException e) {
      return oauthError("invalid_grant");
    }
    if (!expectedUse.equals(decoded.getClaimAsString(TOKEN_USE_CLAIM))) {
      return oauthError("invalid_grant");
    }

    Map<String, Object> identity = identityFrom(decoded);
    Map<String, Object> idClaims = new LinkedHashMap<>(identity);
    idClaims.put("email_verified", true);
    idClaims.put("aud", config.getSecurity().getClientId());

    Map<String, Object> accessClaims = new LinkedHashMap<>(identity);
    accessClaims.put("client_id", config.getSecurity().getClientId());
    accessClaims.put("scope", config.getSecurity().getAuthLoginScope());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("access_token", sign(accessClaims, TOKEN_USE_ACCESS, TOKEN_TTL));
    body.put("id_token", sign(idClaims, TOKEN_USE_ID, TOKEN_TTL));
    // Cognito does not rotate refresh tokens, but returning a fresh one keeps this stateless
    body.put("refresh_token", sign(identity, TOKEN_USE_REFRESH, REFRESH_TTL));
    body.put("token_type", "Bearer");
    body.put("expires_in", TOKEN_TTL.toSeconds());
    return ResponseEntity.ok(body);
  }

  @GetMapping(value = "/oauth2/userInfo", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> userInfo(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Jwt accessToken;
    try {
      accessToken = accessTokenDecoder.decode(authorization.substring("Bearer ".length()));
    } catch (JwtException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("sub", accessToken.getSubject());
    info.put("email", accessToken.getClaimAsString("email"));
    info.put("email_verified", "true");
    info.put("given_name", accessToken.getClaimAsString("given_name"));
    info.put("family_name", accessToken.getClaimAsString("family_name"));
    info.put("username", accessToken.getSubject());
    return ResponseEntity.ok(info);
  }

  @GetMapping("/logout")
  public ResponseEntity<Void> logout(@RequestParam("logout_uri") String logoutUri) {
    requireLocalUri(logoutUri);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(logoutUri)).build();
  }

  /**
   * Stable per email, so logging in again with the same email reuses the same database user.
   */
  private static String subjectFor(String email) {
    return "dev-" + UUID.nameUUIDFromBytes(
        email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> identityFrom(Jwt jwt) {
    Map<String, Object> identity = new LinkedHashMap<>();
    for (String claim : List.of("sub", "email", "given_name", "family_name", "cognito:groups")) {
      Object value = jwt.getClaims().get(claim);
      if (value != null) {
        identity.put(claim, value);
      }
    }
    return identity;
  }

  private String sign(Map<String, Object> claims, String tokenUse, Duration ttl) {
    Instant now = Instant.now();
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
        .issuer("snap2snomed-devauth")
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(ttl)))
        .claim(TOKEN_USE_CLAIM, tokenUse);
    claims.forEach(builder::claim);
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign dev token", e);
    }
    return jwt.serialize();
  }

  /**
   * Avoids turning this into an open redirect: only local UIs may be sent back to.
   */
  private static void requireLocalUri(String uri) {
    String host;
    try {
      host = URI.create(uri).getHost();
    } catch (IllegalArgumentException e) {
      host = null;
    }
    if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "devauth only redirects to localhost, got " + uri);
    }
  }

  private static ResponseEntity<Map<String, Object>> oauthError(String error) {
    return ResponseEntity.badRequest().body(Map.of("error", error));
  }
}
