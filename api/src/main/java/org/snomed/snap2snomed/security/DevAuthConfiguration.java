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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import lombok.extern.slf4j.Slf4j;

/**
 * LOCAL DEVELOPMENT ONLY. Replaces AWS Cognito with a minimal built-in identity provider so the
 * API and UI can be run and tested without a Cognito user pool.
 *
 * <p>When the "devauth" profile is active:
 * <ul>
 *   <li>{@link DevAuthController} serves Cognito-compatible {@code /login}, {@code /oauth2/token},
 *   {@code /oauth2/userInfo} and {@code /logout} endpoints under {@code /dev-auth}.</li>
 *   <li>Tokens are signed with an RSA key generated in memory at startup, so they become invalid
 *   whenever the API restarts.</li>
 *   <li>The JWT decoders below replace {@link JwtDecoderConfigurer}, which is disabled.</li>
 * </ul>
 *
 * <p>Never enable this profile in a deployed environment: anyone can log in as anyone.
 */
@Slf4j
@Configuration
@Profile(DevAuthConfiguration.PROFILE)
public class DevAuthConfiguration {

  public static final String PROFILE = "devauth";
  public static final String BASE_PATH = "/dev-auth";

  public static final String TOKEN_USE_CLAIM = "token_use";
  public static final String TOKEN_USE_ACCESS = "access";
  public static final String TOKEN_USE_ID = "id";
  public static final String TOKEN_USE_REFRESH = "refresh";
  public static final String TOKEN_USE_CODE = "code";

  @Bean
  KeyPair devAuthKeyPair() throws NoSuchAlgorithmException {
    log.warn("**************************************************************************");
    log.warn("* '{}' profile is active: AUTHENTICATION IS FAKE. Local development only. *", PROFILE);
    log.warn("**************************************************************************");
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  @Bean
  @Primary
  JwtDecoder jwtDecoder(KeyPair devAuthKeyPair) {
    return decoderFor(devAuthKeyPair, TOKEN_USE_ACCESS);
  }

  @Bean
  JwtDecoder idTokenDecoder(KeyPair devAuthKeyPair) {
    return decoderFor(devAuthKeyPair, TOKEN_USE_ID);
  }

  /**
   * Validates authorisation codes and refresh tokens issued by {@link DevAuthController}.
   */
  @Bean
  JwtDecoder devAuthGrantDecoder(KeyPair devAuthKeyPair) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withPublicKey((RSAPublicKey) devAuthKeyPair.getPublic()).build();
    decoder.setJwtValidator(new JwtTimestampValidator());
    return decoder;
  }

  /**
   * The fake identity provider endpoints are public, and are kept out of the main filter chain so
   * that {@link PreAuthFilter} does not reject them.
   */
  @Bean
  @Order(1)
  SecurityFilterChain devAuthFilterChain(HttpSecurity http,
      @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    http
        .securityMatcher(BASE_PATH + "/**")
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  private static JwtDecoder decoderFor(KeyPair keyPair, String tokenUse) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(),
        new JwtClaimValidator<String>(TOKEN_USE_CLAIM, tokenUse::equals));
    decoder.setJwtValidator(validator);
    return decoder;
  }
}
