package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.auth.TeamCategory;

public interface OrderExcelTeamCategoryRepository extends JpaRepository<TeamCategory, Long> {
    List<TeamCategory> findByTeam_IdOrderByNameAsc(Long teamId);
    List<TeamCategory> findByTeam_IdAndNameIgnoreCase(Long teamId, String name);

    @Query(value = "select * from tb_team_category tc where tc.team_id = :teamId and replace(coalesce(tc.name, ''), ' ', '') = :normalizedName", nativeQuery = true)
    List<TeamCategory> findByTeamIdAndNameWithoutSpaces(@Param("teamId") Long teamId, @Param("normalizedName") String normalizedName);

    Optional<TeamCategory> findByIdAndTeam_Id(Long id, Long teamId);
}
