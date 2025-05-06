package com.dev.HiddenBATHAuto.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Team;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long>{

	Team findByName(String name);
}
