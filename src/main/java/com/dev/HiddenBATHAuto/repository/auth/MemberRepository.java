package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByPhone(String phone);
    
	Optional<Member> findByUsername(String username);

	boolean existsByPhone(String phone);

	boolean existsByUsername(String username);

	@Query("SELECT m.team.name FROM Member m WHERE m.id = :memberId")
	String findTeamNameByMemberId(@Param("memberId") Long memberId);

	@Query("SELECT m.teamCategory.name FROM Member m WHERE m.id = :memberId")
	String findTeamCategoryNameByMemberId(@Param("memberId") Long memberId);
	
	@Query("SELECT m FROM Member m WHERE m.team.name = :teamName")
	List<Member> findByTeamName(@Param("teamName") String teamName);

	List<Member> findByCompanyAndRole(Company company, MemberRole role);
	
	List<Member> findByCompany(Company company);
	
	List<Member> findByRoleIn(List<MemberRole> roles);
	
	@Query("""
		    SELECT m FROM Member m
		    WHERE m.role IN :roles
		      AND (:name IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')))
		      AND (:team IS NULL OR LOWER(m.team.name) LIKE LOWER(CONCAT('%', :team, '%')))
		""")
	Page<Member> searchByRolesAndNameAndTeam(
	    @Param("roles") List<MemberRole> roles,
	    @Param("name") String name,
	    @Param("team") String team,
	    Pageable pageable);
	
	@Query("""
        select m
          from Member m
         where (:name is null or trim(:name) = '' or lower(m.name) like lower(concat('%', :name, '%')))
           and (:teamId is null or m.team.id = :teamId)
    """)
    Page<Member> searchEmployees(@Param("name") String name,
                                 @Param("teamId") Long teamId,
                                 Pageable pageable);


}