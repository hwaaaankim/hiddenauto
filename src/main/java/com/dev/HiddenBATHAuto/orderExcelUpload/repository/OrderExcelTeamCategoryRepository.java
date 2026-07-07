package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.auth.TeamCategory;

public interface OrderExcelTeamCategoryRepository extends JpaRepository<TeamCategory, Long> {
    List<TeamCategory> findByTeam_NameOrderByNameAsc(String teamName);
    List<TeamCategory> findByTeam_NameAndNameIgnoreCase(String teamName, String name);

    @Query(value = """
            select tc.*
            from tb_team_category tc
            join tb_team t on t.id = tc.team_id
            where t.name = :teamName
              and replace(coalesce(tc.name, ''), ' ', '') = :normalizedName
            order by tc.id asc
            """, nativeQuery = true)
    List<TeamCategory> findByTeamNameAndNameWithoutSpaces(@Param("teamName") String teamName, @Param("normalizedName") String normalizedName);

    Optional<TeamCategory> findByIdAndTeam_Name(Long id, String teamName);
}
