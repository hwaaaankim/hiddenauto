package com.dev.HiddenBATHAuto.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long>{

	Optional<Member> findByUsername(String username);
	
	boolean existsByUsername(String username);
}
