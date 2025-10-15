package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;

@Repository
public interface TeamCategoryRepository extends JpaRepository<TeamCategory, Long> {

	Optional<TeamCategory> findByName(String name);

	Optional<TeamCategory> findByNameAndTeam(String name, Team team);
	
	@Query("SELECT tc FROM TeamCategory tc WHERE tc.team.name = :teamName")
	List<TeamCategory> findByTeamName(@Param("teamName") String teamName);
	
	List<TeamCategory> findByTeamId(Long teamId);

}
