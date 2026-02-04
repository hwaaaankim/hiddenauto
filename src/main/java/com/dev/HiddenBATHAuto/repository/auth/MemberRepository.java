package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    // ✅ 휴대폰 중복체크(본인 제외)
    boolean existsByPhoneAndIdNot(String phone, Long id);
	
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

	List<Member> findByCompany_Id(Long companyId); // ✅ 회사 소속 멤버 조회

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
         where m.company is null
           and m.team is not null
           and m.role in :roles
           and (:name is null or trim(:name) = '' or lower(m.name) like lower(concat('%', :name, '%')))
           and (:teamId is null or m.team.id = :teamId)
    """)
    Page<Member> searchEmployees(@Param("name") String name,
                                 @Param("teamId") Long teamId,
                                 @Param("roles") java.util.List<MemberRole> roles,
                                 Pageable pageable);

	 @Query("""
        SELECT m
        FROM Member m
        WHERE m.company.id IN :companyIds
        ORDER BY m.company.id ASC, m.id ASC
    """)
    List<Member> findAllByCompanyIds(@Param("companyIds") List<Long> companyIds);
	 

    /**
     * ✅ 엑셀용: 연관관계까지 같이 로딩하여 N+1 방지
     * - Sort 파라미터로 정렬 반영
     */
    @EntityGraph(attributePaths = {
            "team",
            "teamCategory",
            "addressScopes",
            "addressScopes.province",
            "addressScopes.city",
            "addressScopes.district"
    })
    @Query("""
        select m
          from Member m
         where m.company is null
           and m.team is not null
           and m.role in :roles
           and (:name is null or trim(:name) = '' or lower(m.name) like lower(concat('%', :name, '%')))
           and (:teamId is null or m.team.id = :teamId)
    """)
    List<Member> searchEmployeesForExcel(@Param("name") String name,
                                         @Param("teamId") Long teamId,
                                         @Param("roles") java.util.List<MemberRole> roles,
                                         Sort sort);
    
    /**
     * ✅ 엑셀(체크된 항목만)용: 연관관계까지 같이 로딩하여 N+1 방지
     * - ids로만 조회 (현재 페이지 체크된 항목만 서버로 넘어옴)
     * - 직원 범위 제한(company null, team not null, role in roles)
     */
    @EntityGraph(attributePaths = {
            "team",
            "teamCategory",
            "addressScopes",
            "addressScopes.province",
            "addressScopes.city",
            "addressScopes.district"
    })
    @Query("""
        select m
          from Member m
         where m.id in :ids
           and m.company is null
           and m.team is not null
           and m.role in :roles
    """)
    List<Member> searchEmployeesForExcelByIds(@Param("ids") List<Long> ids,
                                              @Param("roles") List<MemberRole> roles);

}