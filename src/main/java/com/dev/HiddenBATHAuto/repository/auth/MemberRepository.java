package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByUsername(String username);

	boolean existsByUsername(String username);

	@Query("SELECT m.team.name FROM Member m WHERE m.id = :memberId")
	String findTeamNameByMemberId(@Param("memberId") Long memberId);

	@Query("SELECT m.teamCategory.name FROM Member m WHERE m.id = :memberId")
	String findTeamCategoryNameByMemberId(@Param("memberId") Long memberId);
	
	@Query("SELECT m FROM Member m WHERE m.team.name = :teamName")
	List<Member> findByTeamName(@Param("teamName") String teamName);
}