package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;

@Repository
public interface MemberRegionRepository extends JpaRepository<MemberRegion, Long> {

    List<MemberRegion> findByDistrict(District district);

    List<MemberRegion> findByMemberId(Long memberId);

    @Query("""
        select mr
          from MemberRegion mr
         where mr.member.id <> :memberId
           and mr.province.id = :provinceId
    """)
    List<MemberRegion> findOthersInSameProvince(
            @Param("memberId") Long memberId,
            @Param("provinceId") Long provinceId
    );

    @Query("""
        select mr
          from MemberRegion mr
          left join fetch mr.province p
          left join fetch mr.city c
          left join fetch mr.district d
         where mr.member.id = :memberId
    """)
    List<MemberRegion> findByMemberIdFetchAll(@Param("memberId") Long memberId);

    /**
     * 같은 Province 내에서, 팀 이름이 같은 다른 멤버들의 MemberRegion 조회.
     *
     * 용도:
     * - 배송/AS/생산 등 팀별 담당 구역 중복 등록 방지
     * - 같은 도/광역시 안에서 같은 팀 담당자의 기존 담당 구역 확인
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

    /**
     * 배송담당자 자동배정용 지역 매칭.
     *
     * 매칭 우선순위는 Repository가 아니라 Service에서 처리합니다.
     *
     * 조회 대상:
     * 1) province만 등록된 담당자
     *    - 예: 경기도 전체, 서울특별시 전체
     *
     * 2) province + city 등록, district null
     *    - 예: 경기도 의정부시 전체
     *    - 예: 경기도 용인시 전체
     *
     * 3) province + district 등록 또는 province + city + district 등록
     *    - 예: 서울특별시 강남구
     *    - 예: 경기도 용인시 수지구
     */
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
                or (:cityId is not null
                    and mr.city is not null
                    and mr.city.id = :cityId
                    and mr.district is null)
                or (:districtId is not null
                    and mr.district is not null
                    and mr.district.id = :districtId)
           )
    """)
    List<MemberRegion> findDeliveryRegionMatches(
            @Param("teamName") String teamName,
            @Param("provinceId") Long provinceId,
            @Param("cityId") Long cityId,
            @Param("districtId") Long districtId
    );

    /**
     * findDeliveryRegionMatches와 동일한 범용 팀 지역 매칭 메서드.
     * 현재 다른 서비스에서 사용 중이면 유지하고,
     * 사용처가 없다면 findDeliveryRegionMatches 하나만 남겨도 됩니다.
     */
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
                or (:cityId is not null
                    and mr.city is not null
                    and mr.city.id = :cityId
                    and mr.district is null)
                or (:districtId is not null
                    and mr.district is not null
                    and mr.district.id = :districtId)
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