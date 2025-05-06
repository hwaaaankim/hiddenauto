package com.dev.HiddenBATHAuto.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;

@Repository
public interface TeamCategoryRepository extends JpaRepository<TeamCategory, Long>{

	TeamCategory findByNameAndTeam(String name, Team team);
}
