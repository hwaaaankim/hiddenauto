package com.dev.HiddenBATHAuto.service.auth;

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

import com.dev.HiddenBATHAuto.model.nonstandard.BigSort;
import com.dev.HiddenBATHAuto.model.nonstandard.MiddleSort;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductColor;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductOption;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductSize;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductTag;
import com.dev.HiddenBATHAuto.repository.repository.ProductBigSortRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductFileRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductImageRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductMiddleSortRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductOptionAddRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductOptionRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductSizeRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductTagRepository;
import com.dev.HiddenBATHAuto.service.product.ProductService;
import com.dev.HiddenBATHAuto.utils.ExcelUtils;

import jakarta.persistence.EntityManager;

@Service
public class ExcelUploadService {

	@Autowired
	ProductBigSortRepository productBigSortRepository;

	@Autowired
	ProductMiddleSortRepository productMiddleSortRepository;

	@Autowired
	ProductImageRepository productImageRepository;

	@Autowired
	ProductFileRepository productFileRepository;

	@Autowired
	ProductService productService;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ProductTagRepository productTagRepository;

	@Autowired
	ProductOptionRepository productOptionRepository;

	@Autowired
	ProductSizeRepository productSizeRepository;

	@Autowired
	ProductColorRepository productColorRepository;

	@Autowired
	ExcelUtils excelUtils;

	@Autowired
	ProductOptionPositionRepository productOptionPositionRepository;
	
	@Autowired
	ProductOptionAddRepository productOptionAddRepository;
	
	@Autowired
	private EntityManager entityManager; // EntityManager 주입

	@Autowired
	private PlatformTransactionManager transactionManager; // TransactionManager 주입

	public List<String> updateProductsFromExcel(MultipartFile file) throws IOException {
		List<String> missingProducts = new ArrayList<>();
		Workbook workbook = new XSSFWorkbook(file.getInputStream());
		Sheet sheet = workbook.getSheetAt(2); // 3번째 시트 (index 2)

		for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
			Row row = sheet.getRow(i);
			if (row != null) {
				String productCode = excelUtils.getCellValue(row.getCell(1)); // 2번째 열 (index 1)
				Optional<Product> productOptional = productRepository.findByProductCode(productCode);

				if (productOptional.isPresent()) {
					Product product = productOptional.get();
					TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
					transactionTemplate.execute(status -> {
						try {
							Long middleSortId = Long.parseLong(excelUtils.getCellValue(row.getCell(8))); // 9번째 열 (index
																											// 8)
							Long bigSortId = Long.parseLong(excelUtils.getCellValue(row.getCell(9))); // 10번째 열 (index
																										// 9)
							int productIndex = Integer.parseInt(excelUtils.getCellValue(row.getCell(15))); // 16번째 열
																											// (index
																											// 15)

							MiddleSort middleSort = productMiddleSortRepository.findById(middleSortId).orElseThrow(
									() -> new RuntimeException("MiddleSort not found with ID: " + middleSortId));
							BigSort bigSort = productBigSortRepository.findById(bigSortId)
									.orElseThrow(() -> new RuntimeException("BigSort not found with ID: " + bigSortId));

							product.setMiddleSort(middleSort);
							product.setBigSort(bigSort);
							product.setProductIndex(productIndex);

							productRepository.save(product);
						} catch (Exception e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
						return null;
					});
				} else {
					missingProducts.add(productCode); // 제품이 존재하지 않으면 목록에 추가
				}
			}
		}

		if (!missingProducts.isEmpty()) {
			System.out.println("Missing products: " + String.join(", ", missingProducts));
		}

		return missingProducts;
	}

	public List<String> uploadExcelEx(MultipartFile file) throws IOException {
		List<String> result = new ArrayList<>();
		ExecutorService executorService = Executors.newSingleThreadExecutor();

		Future<?> deleteFuture = executorService.submit(() -> {
			TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
			transactionTemplate.execute(status -> {
				try {
					productRepository.deleteAll();
					productFileRepository.deleteAll();
					productImageRepository.deleteAll();
				} catch (Exception e) {
					System.out.println(e);
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

					if (row != null) {
						TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
						transactionTemplate.execute(status -> {
							try {
								Product product = new Product();
								String codeValue = excelUtils.getCellValue(row.getCell(1));
								String nameValue = excelUtils.getCellValue(row.getCell(2));
								String signStr = excelUtils.getCellValue(row.getCell(3));
								String titleValue = excelUtils.getCellValue(row.getCell(4));
								String subjectValue = excelUtils.getCellValue(row.getCell(5));
								String tissueSignStr = excelUtils.getCellValue(row.getCell(6));
								String tissueAddStr = excelUtils.getCellValue(row.getCell(7));
								String tissuePositionStr = excelUtils.getCellValue(row.getCell(8));
								String drySignStr = excelUtils.getCellValue(row.getCell(9));
								String dryAddStr = excelUtils.getCellValue(row.getCell(10));
								String dryPositionStr = excelUtils.getCellValue(row.getCell(11));
								String concentSignStr = excelUtils.getCellValue(row.getCell(12));
								String concentAddStr = excelUtils.getCellValue(row.getCell(13));
								String concentPositionStr = excelUtils.getCellValue(row.getCell(14));
								String normalLedSignStr = excelUtils.getCellValue(row.getCell(15));
								String normalLedAddStr = excelUtils.getCellValue(row.getCell(16));
								String lowLedSignStr = excelUtils.getCellValue(row.getCell(17));
								String lowLedAddStr = excelUtils.getCellValue(row.getCell(18));
								String lowLedPositionStr = excelUtils.getCellValue(row.getCell(19));
								String handleSignStr = excelUtils.getCellValue(row.getCell(20));
								String handleAddStr = excelUtils.getCellValue(row.getCell(21));
								String orderStr = excelUtils.getCellValue(row.getCell(22));
								Long middleValue = Long.parseLong(excelUtils.getCellValue(row.getCell(23)));
								Long bigValue = Long.parseLong(excelUtils.getCellValue(row.getCell(24)));
								String tagStr = excelUtils.getCellValue(row.getCell(25));
								String optionStr = excelUtils.getCellValue(row.getCell(26));
								String sizeStr = excelUtils.getCellValue(row.getCell(27));
								String colorStr = excelUtils.getCellValue(row.getCell(28));
								int index = Integer.parseInt(excelUtils.getCellValue(row.getCell(30)));
								String changeSignStr = excelUtils.getCellValue(row.getCell(31));
								String widthMinLimit = excelUtils.getCellValue(row.getCell(32));
								String widthMaxLimit = excelUtils.getCellValue(row.getCell(33));
								String heightMinLimit = excelUtils.getCellValue(row.getCell(34));
								String heightMaxLimit = excelUtils.getCellValue(row.getCell(35));
								String depthMinLimit = excelUtils.getCellValue(row.getCell(36));
								String depthMaxLimit = excelUtils.getCellValue(row.getCell(37));
								String doorAmountSignStr = excelUtils.getCellValue(row.getCell(38));
								String doorRatioSignStr = excelUtils.getCellValue(row.getCell(39));

								Boolean signValue = "TRUE".equalsIgnoreCase(signStr.trim());
								Boolean orderValue = "TRUE".equalsIgnoreCase(orderStr.trim());
								Boolean changeSignValue = "TRUE".equalsIgnoreCase(changeSignStr.trim());
								Boolean normalLedSignValue = "TRUE".equalsIgnoreCase(normalLedSignStr.trim());
								Boolean tissueSignValue = "TRUE".equalsIgnoreCase(tissueSignStr.trim());
								Boolean drySignValue = "TRUE".equalsIgnoreCase(drySignStr.trim());
								Boolean concentSignValue = "TRUE".equalsIgnoreCase(concentSignStr.trim());
								Boolean lowLedSignValue = "TRUE".equalsIgnoreCase(lowLedSignStr.trim());
								Boolean handleSignValue = "TRUE".equalsIgnoreCase(handleSignStr.trim());
								Boolean doorAmountSignValue = "TRUE".equalsIgnoreCase(doorAmountSignStr.trim());
								Boolean doorRatioSignValue = "TRUE".equalsIgnoreCase(doorRatioSignStr.trim());

								if (!"NULL".equals(tagStr)) {
									String[] tagArr = tagStr.split(",");
									List<ProductTag> tagList = new ArrayList<>();
									for (String id : tagArr) {
										tagList.add(productTagRepository.findById(Long.parseLong(id))
												.orElseThrow(() -> new RuntimeException("Tag not found")));
									}
									product.setProductTags(tagList);
								}

								if (!"NULL".equals(optionStr)) {
									String[] optionArr = optionStr.split(",");
									List<ProductOption> optionList = new ArrayList<>();
									for (String id : optionArr) {
										optionList.add(productOptionRepository.findById(Long.parseLong(id))
												.orElseThrow(() -> new RuntimeException("Option not found")));
									}
									product.setProductOptions(optionList);
								}

								if (!"NULL".equals(sizeStr)) {
									String[] sizeArr = sizeStr.split(",");
									List<ProductSize> sizeList = new ArrayList<>();
									for (String id : sizeArr) {
										sizeList.add(productSizeRepository.findById(Long.parseLong(id))
												.orElseThrow(() -> new RuntimeException("Size not found")));
									}
									product.setProductSizes(sizeList);
								}

								if (!"NULL".equals(colorStr)) {
									String[] colorArr = colorStr.split(",");
									List<ProductColor> colorList = new ArrayList<>();
									for (String id : colorArr) {
										ProductColor color = productColorRepository.findById(Long.parseLong(id))
												.orElseThrow(() -> new RuntimeException("Color not found"));
										colorList.add(entityManager.merge(color)); // merge 사용하여 detached 상태 해결
									}
									product.setProductColors(colorList);
								}

								product.setProductCode(codeValue);
								product.setName(nameValue);
								product.setProductSign(signValue);
								product.setTitle(titleValue);
								product.setSubject(subjectValue);
								product.setOrder(orderValue);
								product.setUnit("EA");
								product.setProductIndex(index);
								product.setBigSort(productBigSortRepository.findById(bigValue)
										.orElseThrow(() -> new RuntimeException("BigSort not found")));
								product.setMiddleSort(productMiddleSortRepository.findById(middleValue)
										.orElseThrow(() -> new RuntimeException("MiddleSort not found")));
								productRepository.save(product);
							} catch (Exception e) {
								e.printStackTrace();
								throw new RuntimeException(e);
							}
							return null;
						});
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		try {
			// 기다리기 작업이 완료될 때까지 기다림
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

	public List<String> uploadExcel(MultipartFile file) throws IOException {
	    List<String> result = new ArrayList<>();
	    ExecutorService executorService = Executors.newSingleThreadExecutor();

	    Future<?> deleteFuture = executorService.submit(() -> {
	        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
	        transactionTemplate.execute(status -> {
	            try {
	                productRepository.deleteAll();
	                productFileRepository.deleteAll();
	                productImageRepository.deleteAll();
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
	                            product.setProductCode(excelUtils.getCellValue(row.getCell(1)));
	                            product.setName(excelUtils.getCellValue(row.getCell(2)));
	                            product.setProductSign(parseBoolean(excelUtils.getCellValue(row.getCell(3))));
	                            product.setTitle(excelUtils.getCellValue(row.getCell(4)));
	                            product.setSubject(excelUtils.getCellValue(row.getCell(5)));
	                            product.setOrder(parseBoolean(excelUtils.getCellValue(row.getCell(22))));
	                            product.setUnit("EA");
	                            product.setProductIndex(parseIntOrDefault(excelUtils.getCellValue(row.getCell(30))));
	                            product.setProductRotationNumber(0);
	                            product.setProductRotationExtension("-");
	                            product.setProductRepImageName("-");
	                            product.setProductRepImagePath("-");
	                            product.setProductRepImageRoad("-");

	                            // Boolean 필드 처리
	                            product.setNormalLedSign(parseBoolean(excelUtils.getCellValue(row.getCell(15))));
	                            product.setTissueAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(6))));
	                            product.setDryAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(9))));
	                            product.setOutletAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(12))));
	                            product.setLowLedAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(17))));
	                            product.setHandleAddSign(parseBoolean(excelUtils.getCellValue(row.getCell(20))));
	                            product.setDoorAmountSign(parseBoolean(excelUtils.getCellValue(row.getCell(38))));
	                            product.setDoorRatioSign(parseBoolean(excelUtils.getCellValue(row.getCell(39))));
	                            product.setSizeChangeSign(parseBoolean(excelUtils.getCellValue(row.getCell(31))));

	                            // 크기 제한 필드 처리
	                            product.setWidthMinLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(32))));
	                            product.setWidthMaxLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(33))));
	                            product.setHeightMinLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(34))));
	                            product.setHeightMaxLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(35))));
	                            product.setDepthMinLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(36))));
	                            product.setDepthMaxLimit(parseIntOrDefault(excelUtils.getCellValue(row.getCell(37))));

	                            // 다대다 매핑 처리
	                            product.setProductTags(parseManyToMany(excelUtils.getCellValue(row.getCell(25)), productTagRepository));
	                            product.setProductOptions(parseManyToMany(excelUtils.getCellValue(row.getCell(26)), productOptionRepository));
	                            product.setProductSizes(parseManyToMany(excelUtils.getCellValue(row.getCell(27)), productSizeRepository));
	                            product.setProductColors(parseManyToMany(excelUtils.getCellValue(row.getCell(28)), productColorRepository));
	                            product.setProductNormalLedAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(16)), productOptionAddRepository));
	                            product.setProductTissueAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(7)), productOptionAddRepository));
	                            product.setProductTissuePositions(parseManyToMany(excelUtils.getCellValue(row.getCell(8)), productOptionPositionRepository));
	                            product.setProductDryAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(10)), productOptionAddRepository));
	                            product.setProductDryPositions(parseManyToMany(excelUtils.getCellValue(row.getCell(11)), productOptionPositionRepository));
	                            product.setProductOutletAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(13)), productOptionAddRepository));
	                            product.setProductOutletPositions(parseManyToMany(excelUtils.getCellValue(row.getCell(14)), productOptionPositionRepository));
	                            product.setProductLowLedAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(18)), productOptionAddRepository));
	                            product.setProductLowLedPositions(parseManyToMany(excelUtils.getCellValue(row.getCell(19)), productOptionPositionRepository));
	                            product.setProductHandleAdds(parseManyToMany(excelUtils.getCellValue(row.getCell(21)), productOptionAddRepository));

	                            // BigSort와 MiddleSort 처리
	                            product.setBigSort(productBigSortRepository.findById(parseLong(excelUtils.getCellValue(row.getCell(24))))
	                                    .orElseThrow(() -> new RuntimeException("BigSort not found")));
	                            product.setMiddleSort(productMiddleSortRepository.findById(parseLong(excelUtils.getCellValue(row.getCell(23))))
	                                    .orElseThrow(() -> new RuntimeException("MiddleSort not found")));

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
	    if (value == null || value.trim().isEmpty() || "NULL".equalsIgnoreCase(value.trim())) {
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
