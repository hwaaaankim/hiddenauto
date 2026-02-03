package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;

@Repository
public interface MemberRegionRepository extends JpaRepository<MemberRegion, Long>{

	List<MemberRegion> findByDistrict(District district);

	List<MemberRegion> findByMemberId(Long memberId);

    @Query("""
        select mr from MemberRegion mr
        where mr.member.id <> :memberId
          and mr.province.id = :provinceId
    """)
    List<MemberRegion> findOthersInSameProvince(Long memberId, Long provinceId);
    
	@Query("""
	    select mr
	    from MemberRegion mr
	    left join fetch mr.province p
	    left join fetch mr.city c
	    left join fetch mr.district d
	    where mr.member.id = :memberId
	""")
	List<MemberRegion> findByMemberIdFetchAll(Long memberId);
	
	/**
     * 같은 Province 내에서, '팀 이름'이 me와 같은(혹은 둘 다 null) 다른 멤버들의 MemberRegion 조회
     * - m.id <> :myId : 본인 제외
     * - mr.province.id = :provinceId : 같은 도
     * - 팀 이름 동일 조건:
     *     ( :teamName IS NULL AND t.name IS NULL ) OR ( t.name = :teamName )
     */
    @Query("""
        select mr
          from MemberRegion mr
          join mr.member m
          left join m.team t
         where m.id <> :myId
           and mr.province.id = :provinceId
           and (
                 (:teamName is null and t is null)
                 or (t is not null and t.name = :teamName)
               )
    """)
    List<MemberRegion> findOthersInSameProvinceAndTeamName(
            @Param("myId") Long myId,
            @Param("provinceId") Long provinceId,
            @Param("teamName") String teamName
    );
    
    @Query("""
        select mr
          from MemberRegion mr
          join mr.member m
          join m.team t
         where m.enabled = true
           and t.name = :teamName
           and mr.province.id = :provinceId
           and (
                   (mr.city is null and mr.district is null)
                or (:cityId is not null and mr.city is not null and mr.city.id = :cityId and mr.district is null)
                or (:districtId is not null and mr.district is not null and mr.district.id = :districtId)
           )
    """)
    List<MemberRegion> findDeliveryRegionMatches(
            @Param("teamName") String teamName,
            @Param("provinceId") Long provinceId,
            @Param("cityId") Long cityId,
            @Param("districtId") Long districtId
    );
    
    @Query("""
        select mr
          from MemberRegion mr
          join mr.member m
          join m.team t
         where m.enabled = true
           and t.name = :teamName
           and mr.province.id = :provinceId
           and (
                   (mr.city is null and mr.district is null)
                or (:cityId is not null and mr.city is not null and mr.city.id = :cityId and mr.district is null)
                or (:districtId is not null and mr.district is not null and mr.district.id = :districtId)
           )
    """)
    List<MemberRegion> findRegionMatchesByTeamName(
            @Param("teamName") String teamName,
            @Param("provinceId") Long provinceId,
            @Param("cityId") Long cityId,
            @Param("districtId") Long districtId
    );
    
    void deleteByMember_Id(Long memberId);

    List<MemberRegion> findByMember_Id(Long memberId);
}
