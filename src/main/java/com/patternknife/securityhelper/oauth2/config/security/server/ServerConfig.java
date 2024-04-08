package com.patternknife.securityhelper.oauth2.config.security.server;


import com.patternknife.securityhelper.oauth2.config.security.converter.auth.endpoint.CustomGrantAuthenticationConverter;
import com.patternknife.securityhelper.oauth2.config.security.errorhandler.auth.authentication.AuthenticationFailureHandlerImpl;
import com.patternknife.securityhelper.oauth2.config.security.errorhandler.resource.authentication.AuthenticationEntryPointToGlobalExceptionHandler;
import com.patternknife.securityhelper.oauth2.config.security.errorhandler.resource.authorization.CustomAccessDeniedHandler;
import com.patternknife.securityhelper.oauth2.config.security.provider.auth.endpoint.CustomAuthenticationProvider;
import com.patternknife.securityhelper.oauth2.config.security.provider.auth.introspectionendpoint.Oauth2OpaqueTokenAuthenticationProvider;
import com.patternknife.securityhelper.oauth2.config.security.provider.resource.introspector.JpaTokenStoringOauth2TokenIntrospector;
import com.patternknife.securityhelper.oauth2.config.security.serivce.CommonOAuth2AuthorizationCycle;
import com.patternknife.securityhelper.oauth2.config.security.serivce.persistence.authorization.OAuth2AuthorizationServiceImpl;
import com.patternknife.securityhelper.oauth2.config.security.serivce.Oauth2AuthenticationHashCheckService;
import com.patternknife.securityhelper.oauth2.config.security.serivce.persistence.client.RegisteredClientRepositoryImpl;
import com.patternknife.securityhelper.oauth2.config.security.serivce.userdetail.AdminDetailsService;
import com.patternknife.securityhelper.oauth2.config.security.serivce.userdetail.ConditionalDetailsService;
import com.patternknife.securityhelper.oauth2.config.security.serivce.userdetail.CustomerDetailsService;
import com.patternknife.securityhelper.oauth2.config.security.token.TokenResponseSuccessHandler;
import com.patternknife.securityhelper.oauth2.config.security.token.generator.CustomDelegatingOAuth2TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class ServerConfig {

    @Bean
    public OAuth2TokenGenerator<OAuth2Token> tokenGenerator() {
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new CustomDelegatingOAuth2TokenGenerator(
                accessTokenGenerator, refreshTokenGenerator);
    }



    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            CommonOAuth2AuthorizationCycle commonOAuth2AuthorizationCycle,
            OAuth2AuthorizationServiceImpl authorizationService,
            CustomerDetailsService customerDetailsService,
            AdminDetailsService adminDetailsService,
            ConditionalDetailsService conditionalDetailsService,
            Oauth2AuthenticationHashCheckService oauth2AuthenticationHashCheckService,
            OAuth2TokenGenerator<?> tokenGenerator,
            RegisteredClientRepositoryImpl registeredClientRepository) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        http.apply(authorizationServerConfigurer);

        authorizationServerConfigurer
                .registeredClientRepository(registeredClientRepository)
                .authorizationService(authorizationService)
                .tokenGenerator(tokenGenerator)
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint
                                .accessTokenResponseHandler(new TokenResponseSuccessHandler(authorizationService))
                                .accessTokenRequestConverter(new CustomGrantAuthenticationConverter())
                                .errorResponseHandler(new AuthenticationFailureHandlerImpl())
                                .authenticationProvider(new CustomAuthenticationProvider(
                                        commonOAuth2AuthorizationCycle, conditionalDetailsService, oauth2AuthenticationHashCheckService, authorizationService
                                ))

                ).tokenIntrospectionEndpoint(tokenIntrospectEndpoint ->
                        tokenIntrospectEndpoint
                           .introspectionRequestConverter(httpServletRequest -> new Oauth2OpaqueTokenAuthenticationProvider(
                                   tokenIntrospector(
                                           authorizationService, customerDetailsService, adminDetailsService
                                   ),authorizationService, customerDetailsService, adminDetailsService
                           ).convert(httpServletRequest))
                                .authenticationProvider(new Oauth2OpaqueTokenAuthenticationProvider(
                                        tokenIntrospector(
                                                authorizationService, customerDetailsService, adminDetailsService
                                        ),authorizationService, customerDetailsService, adminDetailsService
                                )).errorResponseHandler(new AuthenticationFailureHandlerImpl()));

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http.csrf(AbstractHttpConfigurer::disable).securityMatcher(endpointsMatcher).authorizeHttpRequests(authorize ->
                        authorize.anyRequest().authenticated());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Primary
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
        bearerTokenResolver.setBearerTokenHeaderName(HttpHeaders.AUTHORIZATION);
        return bearerTokenResolver;
    }

    @Bean
    public OpaqueTokenIntrospector tokenIntrospector(OAuth2AuthorizationServiceImpl authorizationService, CustomerDetailsService customerDetailsService, AdminDetailsService adminDetailsService) {
        return new JpaTokenStoringOauth2TokenIntrospector(authorizationService, customerDetailsService, adminDetailsService);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http, OAuth2AuthorizationServiceImpl authorizationService,
                                                                 CustomerDetailsService customerDetailsService, AdminDetailsService adminDetailsService,
                                                                 HandlerExceptionResolver handlerExceptionResolver
                                                                 ) throws Exception {

        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        resolver.setAllowFormEncodedBodyParameter(true);

        http.csrf(AbstractHttpConfigurer::disable)
                        .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(resolver)
                                .authenticationEntryPoint(new AuthenticationEntryPointToGlobalExceptionHandler(handlerExceptionResolver))
                                .accessDeniedHandler(new CustomAccessDeniedHandler())
                        .opaqueToken(opaqueToken -> opaqueToken.introspector(tokenIntrospector(authorizationService, customerDetailsService, adminDetailsService))));

        return http.build();
    }


}