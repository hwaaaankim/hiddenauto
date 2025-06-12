package com.dev.HiddenBATHAuto.service.standard;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardProduct;
import com.dev.HiddenBATHAuto.model.standard.StandardProductColor;
import com.dev.HiddenBATHAuto.model.standard.StandardProductOptionPosition;
import com.dev.HiddenBATHAuto.model.standard.StandardProductPrice;
import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;
import com.dev.HiddenBATHAuto.model.standard.StandardProductSize;
import com.dev.HiddenBATHAuto.repository.standard.StandardCategoryRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductColorRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductPriceRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductSizeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StandardUploadService {

	private final StandardCategoryRepository categoryRepository;
	private final StandardProductSeriesRepository seriesRepository;
	private final StandardProductColorRepository colorRepository;
	private final StandardProductSizeRepository sizeRepository;
	private final StandardProductOptionPositionRepository positionRepository;
	private final StandardProductPriceRepository priceRepository;
	private final StandardProductRepository productRepository;

	public void uploadBaseInfo(MultipartFile file) throws IOException {
		Workbook workbook = WorkbookFactory.create(file.getInputStream());

		// 1. 대분류
		Sheet categorySheet = workbook.getSheet("대분류");
		Map<Long, StandardCategory> categoryMap = new HashMap<>();
		for (Row row : categorySheet) {
			if (row.getRowNum() == 0)
				continue;
			String name = row.getCell(1).getStringCellValue();
			StandardCategory category = new StandardCategory();
			category.setName(name);
			categoryRepository.save(category);
			categoryMap.put((long) row.getCell(0).getNumericCellValue(), category);
		}

		// 2. 중분류
		Sheet seriesSheet = workbook.getSheet("중분류");
		for (Row row : seriesSheet) {
			if (row.getRowNum() == 0)
				continue;
			Long parentId = (long) row.getCell(0).getNumericCellValue();
			String name = row.getCell(2).getStringCellValue();
			StandardProductSeries series = new StandardProductSeries();
			series.setName(name);
			series.setCategory(categoryMap.get(parentId));
			seriesRepository.save(series);
		}

		// 3. 색상
		Sheet colorSheet = workbook.getSheet("제품색상정보");
		for (Row row : colorSheet) {
			if (row.getRowNum() == 0)
				continue;
			StandardProductColor color = new StandardProductColor();
			color.setCode(row.getCell(1).getStringCellValue());
			color.setTitle(row.getCell(2).getStringCellValue());
			color.setNameKr(row.getCell(3).getStringCellValue());
			colorRepository.save(color);
		}

		// 4. 사이즈
		Sheet sizeSheet = workbook.getSheet("사이즈정보");
		for (Row row : sizeSheet) {
			if (row.getRowNum() == 0)
				continue;
			StandardProductSize size = new StandardProductSize();
			size.setCode(row.getCell(1).getStringCellValue());
			size.setTitle(row.getCell(2).getStringCellValue());
			sizeRepository.save(size);
		}

		// 5. 위치
		Sheet positionSheet = workbook.getSheet("제품옵션위치정보");
		for (Row row : positionSheet) {
			if (row.getRowNum() == 0)
				continue;

			Cell codeCell = row.getCell(1);
			Cell nameCell = row.getCell(2);

			String code = (codeCell != null) ? codeCell.toString().trim() : null;
			String nameKr = (nameCell != null) ? nameCell.toString().trim() : null;

			if (code == null || nameKr == null || code.isEmpty() || nameKr.isEmpty()) {
				continue; // skip empty or invalid rows
			}

			StandardProductOptionPosition pos = new StandardProductOptionPosition();
			pos.setCode(code);
			pos.setNameKr(nameKr);
			positionRepository.save(pos);
		}
	}

	@PersistenceContext
    private EntityManager em;
	
	@Transactional
	public void uploadProductInfo(MultipartFile file) throws IOException {
		deleteAllProductData();
		Workbook workbook = WorkbookFactory.create(file.getInputStream());

		// 1. 마스터 테이블 캐시
		Map<Long, StandardCategory> categoryMap = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(StandardCategory::getId, c -> c));
		Map<Long, StandardProductSeries> seriesMap = seriesRepository.findAll().stream()
				.collect(Collectors.toMap(StandardProductSeries::getId, s -> s));
		Map<Long, StandardProductColor> colorMap = colorRepository.findAll().stream()
				.collect(Collectors.toMap(StandardProductColor::getId, c -> c));
		Map<Long, StandardProductSize> sizeMap = sizeRepository.findAll().stream()
				.collect(Collectors.toMap(StandardProductSize::getId, s -> s));
		Map<Long, StandardProductOptionPosition> positionMap = positionRepository.findAll().stream()
				.collect(Collectors.toMap(StandardProductOptionPosition::getId, p -> p));

		Map<String, StandardProduct> productCodeMap = new HashMap<>();

		// 2. 제품정보 시트
		Sheet sheet = workbook.getSheet("제품정보");
		for (Row row : sheet) {
			if (row.getRowNum() == 0)
				continue;

			String productCode = getStringValue(row.getCell(1));
			String name = getStringValue(row.getCell(2));
			Long seriesId = parseId(row.getCell(11));
			Long categoryId = parseId(row.getCell(12));
			Integer indexOrder = parseInteger(row.getCell(15));
			Integer viewCount = parseInteger(row.getCell(16));
			Integer orderCount = parseInteger(row.getCell(17));

			if (productCode == null || name == null)
				continue;

			boolean hasTissue = parseBoolean(row.getCell(3));
			boolean hasDry = parseBoolean(row.getCell(5));
			boolean hasOutlet = parseBoolean(row.getCell(7));
			boolean hasLed = parseBoolean(row.getCell(9));

			StandardProduct p = new StandardProduct();
			p.setProductCode(productCode);
			p.setName(name);
			p.setProductSeries(seriesMap.get(seriesId));
			p.setCategory(categoryMap.get(categoryId));
			p.setIndexOrder(indexOrder != null ? indexOrder : 0);
			p.setViewCount(viewCount != null ? viewCount : 0);
			p.setOrderCount(orderCount != null ? orderCount : 0);
			p.setHasTissueCap(hasTissue);
			p.setHasDryHolder(hasDry);
			p.setHasOutlet(hasOutlet);
			p.setHasLed(hasLed);

			// 연관: 사이즈/색상 (존재할 때만)
			p.setSizes(parseIdList(row.getCell(13), sizeMap));
			p.setColors(parseIdList(row.getCell(14), colorMap));

			// 연관: 위치 옵션 4종 (해당 옵션이 true일 때만 처리)
			p.setProductTissuePositions(hasTissue ? parseIdList(row.getCell(4), positionMap) : Collections.emptyList());
			p.setProductDryPositions(hasDry ? parseIdList(row.getCell(6), positionMap) : Collections.emptyList());
			p.setProductOutletPositions(hasOutlet ? parseIdList(row.getCell(8), positionMap) : Collections.emptyList());
			p.setProductLedPositions(hasLed ? parseIdList(row.getCell(10), positionMap) : Collections.emptyList());

			productRepository.save(p);
			productCodeMap.put(productCode, p);
		}

		// 3. 가격정보 시트
		Sheet priceSheet = workbook.getSheet("가격정보");
		for (Row row : priceSheet) {
			if (row.getRowNum() == 0)
				continue;

			String productCode = getStringValue(row.getCell(1));
			Long sizeId = parseId(row.getCell(3));      // null 허용
			Long colorId = parseId(row.getCell(6));     // null 허용
			Integer price = parseInteger(row.getCell(8));

			if (productCode == null) continue;          // 제품코드는 필수
			if (price == null) price = 99999;

			StandardProduct product = productCodeMap.get(productCode);
			if (product == null) continue;

			StandardProductPrice pp = new StandardProductPrice();
			pp.setProduct(product);
			pp.setSize(sizeMap.get(sizeId));            // null 허용
			pp.setColor(colorMap.get(colorId));         // null 허용
			pp.setPrice(price);

			priceRepository.save(pp);
		}

	}

	@Transactional
    public void deleteAllProductData() {
        em.createNativeQuery("DELETE FROM tb_standard_product_and_led_position").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product_and_outlet_position").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product_and_dry_position").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product_and_tissue_position").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product_color_map").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product_size_map").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product_price").executeUpdate();
        em.createNativeQuery("DELETE FROM tb_standard_product").executeUpdate();
    }
	
	private String getStringValue(Cell cell) {
		return (cell == null) ? null : cell.toString().trim();
	}

	private Boolean parseBoolean(Cell cell) {
		if (cell == null)
			return false;
		String value = cell.toString().trim().toLowerCase();
		return value.equals("true");
	}

	private Long parseId(Cell cell) {
		try {
			return (cell == null) ? null : (long) Double.parseDouble(cell.toString().trim());
		} catch (Exception e) {
			return null;
		}
	}

	private Integer parseInteger(Cell cell) {
		try {
			return (cell == null) ? null : (int) Double.parseDouble(cell.toString().trim());
		} catch (Exception e) {
			return null;
		}
	}

	private <T> List<T> parseIdList(Cell cell, Map<Long, T> map) {
		if (cell == null || cell.toString().trim().isEmpty())
			return Collections.emptyList();
		return Arrays.stream(cell.toString().split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> {
			try {
				return (long) Double.parseDouble(s);
			} catch (NumberFormatException e) {
				return null;
			}
		}).filter(Objects::nonNull).map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
	}

}
