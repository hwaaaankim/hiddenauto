package com.dev.HiddenBATHAuto.service.nonstandard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductProductSortRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionAddRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.utils.ExcelUtils;

@Service
public class ExcelUploadService {

	@Autowired
	ProductProductSortRepository productProductSortRepository;

	@Autowired
	ProductSeriesRepository productSeriesRepository;

	@Autowired
	ProductService productService;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ProductColorRepository productColorRepository;

	@Autowired
	ExcelUtils excelUtils;
	@Autowired
	ProductOptionPositionRepository productOptionPositionRepository;
	@Autowired
	ProductOptionAddRepository productOptionAddRepository;

	@Autowired
	private PlatformTransactionManager transactionManager; // TransactionManager 주입

	public List<String> uploadExcel(MultipartFile file) throws IOException {
	    List<String> result = new ArrayList<>();
	    ExecutorService executorService = Executors.newSingleThreadExecutor();

	    Future<?> deleteFuture = executorService.submit(() -> {
	        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
	        transactionTemplate.execute(status -> {
	            try {
	                productRepository.deleteAll();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	            return null;
	        });
	    });

	    Future<?> insertFuture = executorService.submit(() -> {
	        try {
	            Workbook workbook = new XSSFWorkbook(file.getInputStream());
	            Sheet productSheet = workbook.getSheetAt(2);

	            for (int i = 1; i < productSheet.getPhysicalNumberOfRows(); i++) {
	                Row row = productSheet.getRow(i);
	                int rowIndex = i; // 복사본 생성
	                System.out.println("Processing Row: " + rowIndex); // 현재 처리 중인 행 로그
	                if (row != null) {
	                    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
	                    transactionTemplate.execute(status -> {
	                        try {
	                            Product product = new Product();

	                            // 단일 필드 매핑
	                            product.setName(excelUtils.getCellValue(row.getCell(0)));
	                            product.setProductClickCount(parseIntOrDefault(excelUtils.getCellValue(row.getCell(1))));
	                            product.setProductOrderCount(parseIntOrDefault(excelUtils.getCellValue(row.getCell(2))));
	                            product.setProductPrice(parseIntOrDefault(excelUtils.getCellValue(row.getCell(3))));
	                            product.setProductIndex(parseIntOrDefault(excelUtils.getCellValue(row.getCell(4))));
	                            product.setProductRepImageName("-");
	                            product.setProductRepImageExtension("-");
	                            product.setProductRepImageOriginalName("-");
	                            product.setProductRepImagePath("-");
	                            product.setProductRepImageRoad("-");

	                            // Boolean 필드 처리
	                            product.setTissueAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(5))));
	                            product.setDryAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(8))));
	                            product.setOutletAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(11))));
	                            product.setNormalLedAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(14))));
	                            product.setLowLedAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(16))));
	                            product.setHandleAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(19))));
	                            product.setDoorAmountSign(parseBoolean(excelUtils.getCellValue(row.getCell(21))));
	                            product.setDoorRatioSign(parseBoolean(excelUtils.getCellValue(row.getCell(22))));
	                            product.setMirrorDirectionSign(parseBoolean(excelUtils.getCellValue(row.getCell(23))));
	                            product.setSizeChangeSign(parseBoolean(excelUtils.getCellValue(row.getCell(24))));
	                            product.setSizeRatioSign(parseBoolean(excelUtils.getCellValue(row.getCell(37))));
	                            
	                            // BigSort와 MiddleSort 처리
	                            product.setSeries(productSeriesRepository.findById(parseLong(excelUtils.getCellValue(row.getCell(25))))
	                            		.orElseThrow(() -> new RuntimeException("Series not found")));
	                            product.setProductSort(productProductSortRepository.findById(parseLong(excelUtils.getCellValue(row.getCell(26))))
	                                    .orElseThrow(() -> new RuntimeException("ProductSort not found")));
	                            
	                            // 크기 제한 필드 처리
	                            product.setWidthMinLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(28))));
	                            product.setWidthMaxLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(29))));
	                            product.setHeightMinLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(30))));
	                            product.setHeightMaxLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(31))));
	                            product.setDepthMinLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(32))));
	                            product.setDepthMaxLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(33))));
	                            product.setBasicWidth(parseIntOrDefault(excelUtils.getCellValue(row.getCell(34))));
	                            product.setBasicHeight(parseIntOrDefault(excelUtils.getCellValue(row.getCell(35))));
	                            product.setBasicDepth(parseIntOrDefault(excelUtils.getCellValue(row.getCell(36))));

	                            // 다대다 매핑 처리
	                            product.setProductColors(parseManyToMany(excelUtils.getCellValue(row.getCell(27)), productColorRepository));
	                            product.setProductNormalLedAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(15)), productOptionAddRepository));
	                            product.setProductTissueAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(6)), productOptionAddRepository));
	                            product.setProductTissuePositions(parseManyToMany(excelUtils.getCellValue(row.getCell(7)), productOptionPositionRepository));
	                            product.setProductDryAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(9)), productOptionAddRepository));
	                            product.setProductDryPositions(parseManyToMany(excelUtils.getCellValue(row.getCell(10)), productOptionPositionRepository));
	                            product.setProductOutletAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(12)), productOptionAddRepository));
	                            product.setProductOutletPositions(parseManyToMany(excelUtils.getCellValue(row.getCell(13)), productOptionPositionRepository));
	                            product.setProductLowLedAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(17)), productOptionAddRepository));
	                            product.setProductLowLedPositions(parseManyToMany(excelUtils.getCellValue(row.getCell(18)), productOptionPositionRepository));
	                            product.setProductHandleAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(20)), productOptionAddRepository));

	                            // 저장
	                            productRepository.save(product);

	                        } catch (Exception e) {
	                            e.printStackTrace();
	                            System.err.println("Error in Row: " + rowIndex);
	                            throw new RuntimeException(e);
	                        }
	                        return null;
	                    });
	                }else{
	                	System.err.println("Row is null at index: " + i); // Null 행 로그	
	                }
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    });

	    try {
	        deleteFuture.get();
	        insertFuture.get();
	    } catch (Exception e) {
	        e.printStackTrace();
	        result.add("Error: " + e.getMessage());
	    }

	    executorService.shutdown();
	    result.add("Success");
	    return result;
	}

	// 유틸리티 메서드
	private Boolean parseBoolean(String value) {
	    if (value == null) return false;
	    return "TRUE".equalsIgnoreCase(value.trim());
	}

	private int parseIntOrDefault(String value) {
	    if (value == null || "NULL".equalsIgnoreCase(value.trim())) return 0;
	    try {
	        return Integer.parseInt(value);
	    } catch (NumberFormatException e) {
	        return 0;
	    }
	}

	private Long parseLong(String value) {
	    if (value == null || "NULL".equalsIgnoreCase(value.trim())) return null;
	    try {
	        return Long.parseLong(value.trim());
	    } catch (NumberFormatException e) {
	        return null;
	    }
	}

	private <T> List<T> parseManyToMany(String value, JpaRepository<T, Long> repository) {
	    if (value == null || value.trim().isEmpty() || "FALSE".equalsIgnoreCase(value.trim())) {
	        return Collections.emptyList();
	    }

	    return Arrays.stream(value.split(","))
	        .map(String::trim)
	        .filter(this::isNumeric)
	        .map(Long::parseLong)
	        .map(repository::findById)
	        .filter(Optional::isPresent)
	        .map(Optional::get)
	        .collect(Collectors.toList());
	}

	private boolean isNumeric(String str) {
	    if (str == null || str.isEmpty()) return false;
	    try {
	        Long.parseLong(str);
	        return true;
	    } catch (NumberFormatException e) {
	        return false;
	    }
	}
}
