package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;

public interface OrderExcelMemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByName(String name);
    List<Member> findByCompany_IdAndRoleOrderByIdAsc(Long companyId, MemberRole role);

    @Query(value = "select * from tb_member m where replace(coalesce(m.name, ''), ' ', '') = :normalizedName order by m.id asc", nativeQuery = true)
    List<Member> findByNameWithoutSpaces(@Param("normalizedName") String normalizedName);

    @Query("select m from Member m where m.role in :roles order by m.name asc, m.id asc")
    List<Member> findByRolesOrderByName(@Param("roles") List<MemberRole> roles);

    @Query("select m from Member m where m.team.id = :teamId order by m.name asc, m.id asc")
    List<Member> findByTeamIdOrderByName(@Param("teamId") Long teamId);

    @Query("select m from Member m where m.team is not null and m.team.name = :teamName order by m.name asc, m.id asc")
    List<Member> findByTeamNameOrderByName(@Param("teamName") String teamName);

    @Query("select m from Member m where m.team is not null and m.team.name = :teamName and m.name = :name order by m.id asc")
    List<Member> findByTeamNameAndName(@Param("teamName") String teamName, @Param("name") String name);

    @Query(value = """
            select m.*
            from tb_member m
            join tb_team t on t.id = m.team_id
            where t.name = :teamName
              and replace(coalesce(m.name, ''), ' ', '') = :normalizedName
            order by m.id asc
            """, nativeQuery = true)
    List<Member> findByTeamNameAndNameWithoutSpaces(@Param("teamName") String teamName, @Param("normalizedName") String normalizedName);
}
