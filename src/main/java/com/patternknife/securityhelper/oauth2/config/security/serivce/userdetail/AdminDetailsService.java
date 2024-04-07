package com.patternknife.securityhelper.oauth2.config.security.serivce.userdetail;


import com.patternknife.securityhelper.oauth2.config.response.error.exception.data.ResourceNotFoundException;
import com.patternknife.securityhelper.oauth2.config.security.dao.OauthClientDetailRepository;
import com.patternknife.securityhelper.oauth2.config.security.principal.AccessTokenUserInfo;
import com.patternknife.securityhelper.oauth2.config.security.principal.AdditionalAccessTokenUserInfo;
import com.patternknife.securityhelper.oauth2.domain.admin.dao.AdminRepository;
import com.patternknife.securityhelper.oauth2.domain.admin.entity.Admin;
import com.patternknife.securityhelper.oauth2.domain.admin.entity.QAdmin;
import com.patternknife.securityhelper.oauth2.domain.admin.entity.QAdminRole;
import com.patternknife.securityhelper.oauth2.domain.role.entity.QRole;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;


@Service
public class AdminDetailsService extends QuerydslRepositorySupport implements UserDetailsService {

    private final JPAQueryFactory jpaQueryFactory;

    private final AdminRepository adminRepository;
    private final OauthClientDetailRepository oauthClientDetailRepository;

    private EntityManager entityManager;

    public AdminDetailsService(AdminRepository adminRepository,
                               @Qualifier("authJpaQueryFactory") JPAQueryFactory jpaQueryFactory, OauthClientDetailRepository oauthClientDetailRepository) {
        super(Admin.class);
        this.adminRepository = adminRepository;
        this.jpaQueryFactory = jpaQueryFactory;
        this.oauthClientDetailRepository = oauthClientDetailRepository;
    }

    @Override
    @PersistenceContext(unitName = "commonEntityManager")
    public void setEntityManager(EntityManager entityManager) {
        super.setEntityManager(entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public UserDetails loadUserByUsername(String username){

        Admin admin = adminRepository.findByIdName(username).orElseThrow(() -> new ResourceNotFoundException("관리자 (ID : \"" + username + "\") 을 찾을 수 없습니다."));
        if(admin.getDeletedAt() != null){
            throw new DisabledException(admin.getIdName() + " 님의 계정은 현재 비활성화 되어 있습니다.");
        }
/*        GoogleOtpResolver adminBO = new GoogleOtpResolver();
        GoogleAuthenticatorKey secretKey = adminBO.generateOtpSecretKey();
        String qrUrl = adminBO.generateOtpSecretQrCodeUrl(admin.getIdName(), secretKey);*/

        return buildAdminForAuthentication(admin, getAuthorities(admin.getId()));

    }

    public Admin findByIdWithOrganizationRole(Long id) {

        final QAdmin qAdmin = QAdmin.admin;
        final QAdminRole qAdminRole = QAdminRole.adminRole;
        final QRole qRole = QRole.role;

        return jpaQueryFactory.selectFrom(qAdmin)
                .leftJoin(qAdmin.adminRoles, qAdminRole).fetchJoin().leftJoin(qAdminRole.role, qRole).fetchJoin()
                .where(qAdmin.id.eq(id)).fetchOne();

    }


    private AccessTokenUserInfo buildAdminForAuthentication(Admin admin, Collection<? extends GrantedAuthority> authorities) {

        String username = admin.getIdName();
        String password = admin.getPassword().getValue();

        boolean enabled = true;
        boolean accountNonExpired = true;
        boolean credentialsNonExpired = true;
        boolean accountNonLocked = true;

        AccessTokenUserInfo authUser = new AccessTokenUserInfo(username, password, enabled, accountNonExpired, credentialsNonExpired,
                accountNonLocked, authorities);

        // Spring Security 로그인 사용자 정보에 DB의 추가적인 컬럼들도 저장하기 위함.
        authUser.setAdditionalAccessTokenUserInfo(new AdditionalAccessTokenUserInfo(admin));

        return authUser;
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Long adminId) {

        Admin admin = findByIdWithOrganizationRole(adminId);

        // Check if getCustomerRoles() returns null
        if (admin.getAdminRoles() == null) {
            // Return an empty authority collection if customer roles are null
            return new ArrayList<GrantedAuthority>();
        }


        String[] adminRoles = admin.getAdminRoles().stream().map((adminRole) -> adminRole.getRole().getName()).toArray(String[]::new);
        Collection<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(adminRoles);
        return authorities;
    }


}