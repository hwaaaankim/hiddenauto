package com.dev.HiddenBATHAuto.model.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class PrincipalDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 2024L;

    /**
     * ✅ 엔티티를 그대로 들고가되,
     * - toString()에서 제외하여 Lazy 컬렉션 접근으로 예외가 터지는 것을 방지
     * - equals/hashCode 생성 자체를 하지 않아(= @Data 제거) 세션 저장/로그 출력 시 위험 감소
     */
    @ToString.Exclude
    private final Member member;

    /**
     * ✅ getAuthorities()가 자주 호출되어도 매번 새 객체 생성하지 않도록 캐시
     */
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * ✅ 로그/디버깅 시 최소 정보만 안전하게 노출
     */
    @ToString.Include
    private final Long id;

    @ToString.Include
    private final String username;

    @ToString.Include
    private final String role;

    public PrincipalDetails(Member member) {
        this.member = member;
        this.id = (member != null ? member.getId() : null);
        this.username = (member != null ? member.getUsername() : null);
        this.role = (member != null && member.getRole() != null ? member.getRole().name() : null);

        // ROLE_ 접두사는 유지
        if (member != null && member.getRole() != null) {
            this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
        } else {
            this.authorities = List.of();
        }
    }

    // ===============================
    // ✅ 기존 코드와 동일한 편의 메서드들
    // (다른 곳 호출 깨지지 않게 유지)
    // ===============================

    public Long getId() {
        return member != null ? member.getId() : null;
    }

    public String getName() {
        return member != null ? member.getName() : null;
    }

    public Company getCompany() {
        return member != null ? member.getCompany() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public String getFullAddress() {
        Company company = (member != null ? member.getCompany() : null);
        if (company == null) return "";
        String road = company.getRoadAddress() != null ? company.getRoadAddress() : "";
        String detail = company.getDetailAddress() != null ? company.getDetailAddress() : "";
        String merged = (road + " " + detail).trim();
        return merged;
    }

    public String getZipCode() {
        Company company = (member != null ? member.getCompany() : null);
        return company != null && company.getZipCode() != null ? company.getZipCode() : "";
    }

    public String getDoName() {
        Company company = (member != null ? member.getCompany() : null);
        return company != null && company.getDoName() != null ? company.getDoName() : "";
    }

    public String getSiName() {
        Company company = (member != null ? member.getCompany() : null);
        return company != null && company.getSiName() != null ? company.getSiName() : "";
    }

    public String getGuName() {
        Company company = (member != null ? member.getCompany() : null);
        return company != null && company.getGuName() != null ? company.getGuName() : "";
    }

    public Team getTeam() {
        return member != null ? member.getTeam() : null;
    }

    public TeamCategory getTeamCategory() {
        return member != null ? member.getTeamCategory() : null;
    }

    public Long getTeamId() {
        Team team = (member != null ? member.getTeam() : null);
        return team != null ? team.getId() : null;
    }

    public Long getTeamCategoryId() {
        TeamCategory tc = (member != null ? member.getTeamCategory() : null);
        return tc != null ? tc.getId() : null;
    }

    public String getTeamName() {
        Team team = (member != null ? member.getTeam() : null);
        return team != null ? team.getName() : null;
    }

    public String getTeamCategoryName() {
        TeamCategory tc = (member != null ? member.getTeamCategory() : null);
        return tc != null ? tc.getName() : null;
    }

    // ===============================
    // ✅ UserDetails 필수 구현
    // ===============================

    @Override
    public String getUsername() {
        return this.username; // member.getUsername() 직접 호출 대신 캐시 사용
    }

    @Override
    public String getPassword() {
        return member != null ? member.getPassword() : null;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Override
    public boolean isEnabled() {
        return member != null && member.isEnabled();
    }

    /**
     * ✅ 기존 코드 호환 유지용
     * (외부에서 principal.getMember() 쓰는 코드가 많다면 유지하는 게 안전합니다)
     */
    public Member getMember() {
        return this.member;
    }
}
