package com.dev.HiddenBATHAuto.model.auth;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;

@Entity
@Table(name = "tb_team")
@Data
@ToString(exclude = "teamCategories")
public class Team implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 예: 생산팀, 배송팀 등

    @OneToMany(mappedBy = "team")
    @JsonManagedReference("team-teamCategory")
    private List<TeamCategory> teamCategories;
}

