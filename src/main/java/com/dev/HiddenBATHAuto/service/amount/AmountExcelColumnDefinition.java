package com.dev.HiddenBATHAuto.service.amount;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.dev.HiddenBATHAuto.dto.amount.AmountExcelColumnDto;

public final class AmountExcelColumnDefinition {

    private AmountExcelColumnDefinition() {
    }

    /**
     * 기존 품목_얼마에요 원본 업로드 양식입니다.
     * 화면 표시 컬럼과 분리해서, 기존 양식 엑셀이 새 동기화 컬럼 때문에 깨지지 않도록 유지합니다.
     */
    public static final List<AmountExcelColumnDto> ITEM_ORIGINAL_IMPORT_COLUMNS = List.of(
            new AmountExcelColumnDto("division", "구분"),
            new AmountExcelColumnDto("itemCode", "코드"),
            new AmountExcelColumnDto("itemName", "품목명"),
            new AmountExcelColumnDto("purchasePrice", "매입단가"),
            new AmountExcelColumnDto("salesPrice", "매출단가"),
            new AmountExcelColumnDto("openingStockQty", "기초재고량"),
            new AmountExcelColumnDto("openingStockUnitPrice", "기초재고단가"),
            new AmountExcelColumnDto("unit", "단위"),
            new AmountExcelColumnDto("specification", "규격"),
            new AmountExcelColumnDto("barcode", "바코드"),
            new AmountExcelColumnDto("categoryName", "분류명"),
            new AmountExcelColumnDto("brandName", "브랜드명"),
            new AmountExcelColumnDto("modelName", "모델명"),
            new AmountExcelColumnDto("taxType", "과세구분"),
            new AmountExcelColumnDto("itemRegisteredDate", "품목등록일자"),
            new AmountExcelColumnDto("liquorItemYn", "주류품목여부"),
            new AmountExcelColumnDto("usageType", "용도구분"),
            new AmountExcelColumnDto("liquorType", "주종구분"),
            new AmountExcelColumnDto("dedicatedWarehouseNo", "전용창고번호"),
            new AmountExcelColumnDto("purchaseBaseQty", "매입기준수량"),
            new AmountExcelColumnDto("properStock", "적정재고"),
            new AmountExcelColumnDto("outsourceProductionPrice", "외주생산단가"),
            new AmountExcelColumnDto("grade1Price", "1등급가"),
            new AmountExcelColumnDto("grade1Qty", "1등급수량"),
            new AmountExcelColumnDto("grade2Price", "2등급가"),
            new AmountExcelColumnDto("grade2Qty", "2등급수량"),
            new AmountExcelColumnDto("grade3Price", "3등급가"),
            new AmountExcelColumnDto("grade3Qty", "3등급수량"),
            new AmountExcelColumnDto("grade4Price", "4등급가"),
            new AmountExcelColumnDto("grade4Qty", "4등급수량"),
            new AmountExcelColumnDto("grade5Price", "5등급가"),
            new AmountExcelColumnDto("grade5Qty", "5등급수량"),
            new AmountExcelColumnDto("useStatus", "사용상태"),
            new AmountExcelColumnDto("stockCalculationYn", "재고계산여부"),
            new AmountExcelColumnDto("originDisplayType", "원산지구분표시"),
            new AmountExcelColumnDto("procurementIdentifierCode", "조달청식별코드"),
            new AmountExcelColumnDto("note", "참고사항"),
            new AmountExcelColumnDto("udiUseYn", "UDI사용여부")
    );


    /**
     * 관리화면 표시/수정 컬럼입니다.
     * 동기화 엑셀에서 보강되는 대분류/중분류/규격/거울재단/동기화메모를 앞쪽에 배치했습니다.
     */
    public static final List<AmountExcelColumnDto> ITEM_COLUMNS = List.of(
            new AmountExcelColumnDto("itemCode", "제품코드"),
            new AmountExcelColumnDto("itemName", "품목명"),
            new AmountExcelColumnDto("categoryName", "대분류"),
            new AmountExcelColumnDto("middleCategoryName", "중분류"),
            new AmountExcelColumnDto("specification", "사이즈"),
            new AmountExcelColumnDto("standard", "규격여부"),
            new AmountExcelColumnDto("mirrorCuttingProduct", "거울재단여부"),
            new AmountExcelColumnDto("salesPrice", "매출단가"),
            new AmountExcelColumnDto("purchasePrice", "매입단가"),
            new AmountExcelColumnDto("unit", "단위"),
            new AmountExcelColumnDto("syncMemo", "동기화메모"),
            new AmountExcelColumnDto("division", "구분"),
            new AmountExcelColumnDto("openingStockQty", "기초재고량"),
            new AmountExcelColumnDto("openingStockUnitPrice", "기초재고단가"),
            new AmountExcelColumnDto("barcode", "바코드"),
            new AmountExcelColumnDto("brandName", "브랜드명"),
            new AmountExcelColumnDto("modelName", "모델명"),
            new AmountExcelColumnDto("taxType", "과세구분"),
            new AmountExcelColumnDto("itemRegisteredDate", "품목등록일자"),
            new AmountExcelColumnDto("liquorItemYn", "주류품목여부"),
            new AmountExcelColumnDto("usageType", "용도구분"),
            new AmountExcelColumnDto("liquorType", "주종구분"),
            new AmountExcelColumnDto("dedicatedWarehouseNo", "전용창고번호"),
            new AmountExcelColumnDto("purchaseBaseQty", "매입기준수량"),
            new AmountExcelColumnDto("properStock", "적정재고"),
            new AmountExcelColumnDto("outsourceProductionPrice", "외주생산단가"),
            new AmountExcelColumnDto("grade1Price", "1등급가"),
            new AmountExcelColumnDto("grade1Qty", "1등급수량"),
            new AmountExcelColumnDto("grade2Price", "2등급가"),
            new AmountExcelColumnDto("grade2Qty", "2등급수량"),
            new AmountExcelColumnDto("grade3Price", "3등급가"),
            new AmountExcelColumnDto("grade3Qty", "3등급수량"),
            new AmountExcelColumnDto("grade4Price", "4등급가"),
            new AmountExcelColumnDto("grade4Qty", "4등급수량"),
            new AmountExcelColumnDto("grade5Price", "5등급가"),
            new AmountExcelColumnDto("grade5Qty", "5등급수량"),
            new AmountExcelColumnDto("useStatus", "사용상태"),
            new AmountExcelColumnDto("stockCalculationYn", "재고계산여부"),
            new AmountExcelColumnDto("originDisplayType", "원산지구분표시"),
            new AmountExcelColumnDto("procurementIdentifierCode", "조달청식별코드"),
            new AmountExcelColumnDto("note", "참고사항"),
            new AmountExcelColumnDto("udiUseYn", "UDI사용여부")
    );

    public static final List<AmountExcelColumnDto> CUSTOMER_COLUMNS = List.of(
            new AmountExcelColumnDto("customerCode", "코드"),
            new AmountExcelColumnDto("division", "구분"),
            new AmountExcelColumnDto("tradeType", "유형"),
            new AmountExcelColumnDto("customerName", "거래처명"),
            new AmountExcelColumnDto("businessName", "상호명"),
            new AmountExcelColumnDto("businessNo", "사업자(주민)번호"),
            new AmountExcelColumnDto("grade", "등급"),
            new AmountExcelColumnDto("accountsReceivable", "외상매출금"),
            new AmountExcelColumnDto("receivable", "미수금"),
            new AmountExcelColumnDto("advancePayment", "선급금"),
            new AmountExcelColumnDto("accountsPayable", "외상매입금"),
            new AmountExcelColumnDto("unpaidAmount", "미지급금"),
            new AmountExcelColumnDto("advanceReceived", "선수금"),
            new AmountExcelColumnDto("subBusinessNo", "종사업자번호"),
            new AmountExcelColumnDto("corporationNo", "법인등록번호"),
            new AmountExcelColumnDto("ceoName", "대표자명"),
            new AmountExcelColumnDto("workplaceAddress1", "사업장주소1"),
            new AmountExcelColumnDto("workplaceAddress2", "사업장주소2"),
            new AmountExcelColumnDto("headOfficeAddress1", "본사주소1"),
            new AmountExcelColumnDto("headOfficeAddress2", "본사주소2"),
            new AmountExcelColumnDto("businessType", "업태"),
            new AmountExcelColumnDto("businessItem", "종목"),
            new AmountExcelColumnDto("telephone", "전화"),
            new AmountExcelColumnDto("fax", "팩스"),
            new AmountExcelColumnDto("mobile", "휴대폰"),
            new AmountExcelColumnDto("actualZipCode", "실제주소_우편번호"),
            new AmountExcelColumnDto("actualAddress1", "실제주소1"),
            new AmountExcelColumnDto("actualAddress2", "실제주소2"),
            new AmountExcelColumnDto("depositBank", "입금은행"),
            new AmountExcelColumnDto("accountNo", "계좌번호"),
            new AmountExcelColumnDto("accountHolder", "예금주"),
            new AmountExcelColumnDto("homepage", "홈페이지주소"),
            new AmountExcelColumnDto("manager1Name", "거래처담당자1_담당자명"),
            new AmountExcelColumnDto("manager1Telephone", "거래처담당자1_전화"),
            new AmountExcelColumnDto("manager1Mobile", "거래처담당자1_휴대폰"),
            new AmountExcelColumnDto("manager1Fax", "거래처담당자1_팩스"),
            new AmountExcelColumnDto("manager1Email", "거래처담당자1_이메일"),
            new AmountExcelColumnDto("smsSendYn", "SMS발송"),
            new AmountExcelColumnDto("faxSendYn", "FAX발송"),
            new AmountExcelColumnDto("accountSubjectCode", "계정과목코드"),
            new AmountExcelColumnDto("accountSubjectName", "계정과목"),
            new AmountExcelColumnDto("categoryName", "분류명"),
            new AmountExcelColumnDto("creditAccountSubjectCode", "외상계정과목코드"),
            new AmountExcelColumnDto("creditAccountSubjectName", "외상계정과목"),
            new AmountExcelColumnDto("transactionCategory", "거래범주"),
            new AmountExcelColumnDto("taxType", "과세구분"),
            new AmountExcelColumnDto("salesManager", "영업담당"),
            new AmountExcelColumnDto("paymentType", "결제유형"),
            new AmountExcelColumnDto("paymentBook", "결제장부"),
            new AmountExcelColumnDto("dedicatedItemUseYn", "전용품목사용"),
            new AmountExcelColumnDto("transactionItemUseYn", "거래품목사용"),
            new AmountExcelColumnDto("salesPriceType", "매출가격"),
            new AmountExcelColumnDto("fixedRateYn", "정률제여부"),
            new AmountExcelColumnDto("fixedRatePercent", "정률제(%)"),
            new AmountExcelColumnDto("useStatus", "사용상태"),
            new AmountExcelColumnDto("reportPrintYn", "보고서출력"),
            new AmountExcelColumnDto("note", "참고사항"),
            new AmountExcelColumnDto("manager2Name", "거래처담당자2_담당자명"),
            new AmountExcelColumnDto("manager2Telephone", "거래처담당자2_전화"),
            new AmountExcelColumnDto("manager2Mobile", "거래처담당자2_휴대폰"),
            new AmountExcelColumnDto("manager2Fax", "거래처담당자2_팩스"),
            new AmountExcelColumnDto("manager2Email", "거래처담당자2_이메일"),
            new AmountExcelColumnDto("manager3Name", "거래처담당자3_담당자명"),
            new AmountExcelColumnDto("manager3Telephone", "거래처담당자3_전화"),
            new AmountExcelColumnDto("manager3Mobile", "거래처담당자3_휴대폰"),
            new AmountExcelColumnDto("manager3Fax", "거래처담당자3_팩스"),
            new AmountExcelColumnDto("manager3Email", "거래처담당자3_이메일"),
            new AmountExcelColumnDto("manager4Name", "거래처담당자4_담당자명"),
            new AmountExcelColumnDto("manager4Telephone", "거래처담당자4_전화"),
            new AmountExcelColumnDto("manager4Mobile", "거래처담당자4_휴대폰"),
            new AmountExcelColumnDto("manager4Fax", "거래처담당자4_팩스"),
            new AmountExcelColumnDto("manager4Email", "거래처담당자4_이메일"),
            new AmountExcelColumnDto("manager5Name", "거래처담당자5_담당자명"),
            new AmountExcelColumnDto("manager5Telephone", "거래처담당자5_전화"),
            new AmountExcelColumnDto("manager5Mobile", "거래처담당자5_휴대폰"),
            new AmountExcelColumnDto("manager5Fax", "거래처담당자5_팩스"),
            new AmountExcelColumnDto("manager5Email", "거래처담당자5_이메일")
    );

    public static final Set<String> ITEM_FIELD_SET = ITEM_COLUMNS.stream()
            .map(AmountExcelColumnDto::field)
            .collect(Collectors.toUnmodifiableSet());

    public static final Set<String> CUSTOMER_FIELD_SET = CUSTOMER_COLUMNS.stream()
            .map(AmountExcelColumnDto::field)
            .collect(Collectors.toUnmodifiableSet());

    public static final Map<String, String> ITEM_HEADER_TO_FIELD = ITEM_COLUMNS.stream()
            .collect(Collectors.toUnmodifiableMap(AmountExcelColumnDto::header, AmountExcelColumnDto::field));

    public static final Map<String, String> CUSTOMER_HEADER_TO_FIELD = CUSTOMER_COLUMNS.stream()
            .collect(Collectors.toUnmodifiableMap(AmountExcelColumnDto::header, AmountExcelColumnDto::field));

    public static boolean isAllowedItemField(String field) {
        return field != null && ITEM_FIELD_SET.contains(field);
    }

    public static boolean isAllowedCustomerField(String field) {
        return field != null && CUSTOMER_FIELD_SET.contains(field);
    }
}
