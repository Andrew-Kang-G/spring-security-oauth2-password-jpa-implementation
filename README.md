# Spring Security Oauth2 Password JPA Implementation
## Overview

* In the Spring Security 6 ecosystem, compared to 5, there is a preference for JSON Web Tokens and Keycloak over traditional OAuth2, and for the Authorization Code flow over the Password Grant method. In this context, the OAuth2 Password Grant method has been implemented with the following advantages:

  * Implemented using JPA.
  * Authentication management based on a combination of username, client id, and an extra token (referred to in the source code as APP TOKEN, which receives a unique value from the calling devices).
  * Separated UserDetails implementation for Admin and Customer roles.
  * Integration with spring-security-oauth2-authorization-server.
  * Provision of MySQL DDL.
  * Application of Spring Rest Docs.

## Dependencies

| Category          | Dependencies                               |
|-------------------|--------------------------------------------|
| Backend-Language  | Java 17                                    |
| Backend-Framework | Spring Boot 3.1.2                          |
| Main Libraries    | Spring Security Authorization Server 1.2.3 |
| Package-Manager   | Maven 3.6.3 (mvnw, Dockerfile)             |
| RDBMS             | Mysql 8.0.17                               |