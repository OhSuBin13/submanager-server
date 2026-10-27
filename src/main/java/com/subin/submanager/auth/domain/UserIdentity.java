package com.subin.submanager.auth.domain;

import java.time.Instant;

import com.subin.submanager.global.entity.BaseTimeEntity;
import com.subin.submanager.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 수단. 로그인 수단마다 별도 계정이라 회원과 1:1이다.
 * providerUserId는 소셜이면 각 회사의 사용자 id, 이메일이면 소문자로 정규화한 이메일 주소다.
 */
@Getter
@Entity
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false)
    private String providerUserId;

    @Column(length = 320)
    private String email;

    @Column(length = 100)
    private String passwordHash;

    private Instant emailVerifiedAt;

    private UserIdentity(User user, AuthProvider provider, String providerUserId, String email, String passwordHash) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static UserIdentity social(User user, AuthProvider provider, String providerUserId, String email) {
        if (provider == AuthProvider.EMAIL) {
            throw new IllegalArgumentException("이메일 가입은 UserIdentity.email(...)을 사용하세요.");
        }
        return new UserIdentity(user, provider, providerUserId, email, null);
    }

    public static UserIdentity email(User user, String email, String passwordHash) {
        String normalized = email.trim().toLowerCase();
        return new UserIdentity(user, AuthProvider.EMAIL, normalized, normalized, passwordHash);
    }

    public void verifyEmail(Instant now) {
        this.emailVerifiedAt = now;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
