package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Team;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long>{

	Optional<Team> findByName(String name);
	
	default List<Team> findAllOrderedByName() {
        return findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

}
