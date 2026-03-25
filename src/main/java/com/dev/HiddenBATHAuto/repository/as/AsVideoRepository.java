package com.dev.HiddenBATHAuto.repository.as;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.task.as.AsVideo;

public interface AsVideoRepository extends JpaRepository<AsVideo, Long> {

    List<AsVideo> findByAsTask_Id(Long asTaskId);

    List<AsVideo> findByAsTask_IdAndIdInAndType(Long asTaskId, List<Long> ids, String type);
    
    List<AsVideo> findByAsTaskIdAndIdInAndType(Long asTaskId, List<Long> ids, String type);
}