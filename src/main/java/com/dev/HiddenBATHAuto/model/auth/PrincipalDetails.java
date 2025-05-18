package com.dev.HiddenBATHAuto.model.auth;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PrincipalDetails implements UserDetails, Serializable {

	private static final long serialVersionUID = 2024L;

	private final Member member;

	public PrincipalDetails(Member member) {
		this.member = member;
	}
	
	public Long getId() {
	    return member.getId();
	}
	
	public String getName() {
	    return member.getName();
	}

	public Company getCompany() {
	    return member.getCompany();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		ArrayList<GrantedAuthority> auth = new ArrayList<>();
		// Spring Security 권한은 "ROLE_" 접두사 필요
		auth.add(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
		return auth;
	}
	
	public String getFullAddress() {
	    Company company = member.getCompany();
	    if (company == null) return "";
	    return company.getRoadAddress() + " " + company.getDetailAddress();
	}

	public String getZipCode() {
	    return member.getCompany() != null ? member.getCompany().getZipCode() : "";
	}

	public String getDoName() {
	    return member.getCompany() != null ? member.getCompany().getDoName() : "";
	}

	public String getSiName() {
	    return member.getCompany() != null ? member.getCompany().getSiName() : "";
	}

	public String getGuName() {
	    return member.getCompany() != null ? member.getCompany().getGuName() : "";
	}

	public Team getTeam() {
		return member.getTeam();
	}
	
	public TeamCategory getTeamCategory() {
		
		return member.getTeamCategory();
	}
	
	public Long getTeamId() {
	    return member.getTeam() != null ? member.getTeam().getId() : null;
	}

	public Long getTeamCategoryId() {
	    return member.getTeamCategory() != null ? member.getTeamCategory().getId() : null;
	}
	
	public String getTeamName() {
	    return member.getTeam() != null ? member.getTeam().getName() : null;
	}

	public String getTeamCategoryName() {
	    return member.getTeamCategory() != null ? member.getTeamCategory().getName() : null;
	}
	
	@Override
	public String getUsername() {
		return this.member.getUsername();
	}

	@Override
	public String getPassword() {
		return this.member.getPassword();
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
		return member.isEnabled(); // 실제 enabled 필드 기반
	}

	public Member getMember() {
		return this.member;
	}
}
