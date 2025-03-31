package com.dev.HiddenBATHAuto.service.calculate;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.calculate.ProductSelectionRequestDTO;

@Service
public class LowCalculateService {

    public void calculateLow(ProductSelectionRequestDTO request) {
        System.out.println("---- 하부장 가격 계산 시작 ----");

        // 필수 정보
        System.out.println("middleSort: " + request.getMiddleSort());
        System.out.println("size: " + request.getSize());
        System.out.println("formofwash: " + request.getFormofwash());

        // 세면대 종류 (formofwash에 따라 다름)
        if ("under".equals(request.getFormofwash())) {
            System.out.println("sortofunder: " + request.getSortofunder());
        } else if ("dogi".equals(request.getFormofwash())) {
            System.out.println("sortofdogi: " + request.getSortofdogi());
        }

        System.out.println("numberofwash: " + request.getNumberofwash());
        System.out.println("colorofmarble: " + request.getColorofmarble());

        // 문 추가
        System.out.println("door: " + request.getDoor());
        if ("add".equals(request.getDoor())) {
            System.out.println("formofdoor_other: " + request.getFormofdoor_other());
            System.out.println("formofdoor_slide: " + request.getFormofdoor_slide());
            System.out.println("numberofdoor: " + request.getNumberofdoor());
        }

        // 마구리
        System.out.println("maguri: " + request.getMaguri());
        if ("add".equals(request.getMaguri())) {
            System.out.println("directionofmaguri: " + request.getDirectionofmaguri());
            System.out.println("sizeofmaguri: " + request.getSizeofmaguri());
        }

        // 상판 타공
        System.out.println("hole: " + request.getHole());

        // 손잡이
        System.out.println("handle: " + request.getHandle());
        if ("add".equals(request.getHandle())) {
            System.out.println("handletype: " + request.getHandletype());
            System.out.println("dolche_color: " + request.getDolche_color());
            // handletype 에 따라 다른 색상 필드가 들어올 수도 있으니 필요시 분기 가능
        }

        // 걸레받이
        System.out.println("board: " + request.getBoard());
        if ("add".equals(request.getBoard())) {
            System.out.println("directionofboard: " + request.getDirectionofboard());
        }

        // 기타 옵션
        System.out.println("led: " + request.getLed());
        System.out.println("outletPosition: " + request.getOutletPosition());
        System.out.println("dryPosition: " + request.getDryPosition());
        System.out.println("tissuePosition: " + request.getTissuePosition());

        System.out.println("---- 하부장 가격 계산 끝 ----");
    }
}