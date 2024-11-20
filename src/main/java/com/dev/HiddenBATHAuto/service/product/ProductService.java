package com.dev.HiddenBATHAuto.service.product;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.ProductDTO;
import com.dev.HiddenBATHAuto.model.product.BigSort;
import com.dev.HiddenBATHAuto.model.product.MiddleSort;
import com.dev.HiddenBATHAuto.model.product.Product;
import com.dev.HiddenBATHAuto.model.product.ProductColor;
import com.dev.HiddenBATHAuto.model.product.ProductOption;
import com.dev.HiddenBATHAuto.model.product.ProductSize;
import com.dev.HiddenBATHAuto.model.product.ProductTag;
import com.dev.HiddenBATHAuto.repository.repository.ProductBigSortRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductMiddleSortRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductOptionRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductSizeRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductTagRepository;

@Service
public class ProductService {

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ProductMiddleSortRepository productMiddleSortRepository;

	@Autowired
	ProductBigSortRepository productBigSortRepository;

	@Autowired
	ProductTagRepository productTagRepository;

	@Autowired
	ProductOptionRepository productOptionRepository;

	@Autowired
	ProductOptionService productOptionService;

	@Autowired
	ProductTagService productTagService;

	@Autowired
	ProductSizeRepository productSizeRepository;

	@Autowired
	ProductSizeService productSizeService;

	@Autowired
	ProductColorRepository productColorRepository;

	@Autowired
	ProductColorService productColorService;

	@Value("${spring.upload.env}")
	private String env;

	@Value("${spring.upload.path}")
	private String commonPath;

	public byte[] processExcelFile(MultipartFile file) throws IOException {
		// 엑셀 파일을 읽어들입니다.
		XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream());
		XSSFSheet sheet = workbook.getSheetAt(2); // 세 번째 시트 (index 2)

		// 셀 스타일 설정
		CellStyle boldRedStyle = workbook.createCellStyle();
		Font boldRedFont = workbook.createFont();
		boldRedFont.setBold(true);
		boldRedFont.setColor(IndexedColors.RED.getIndex());
		boldRedStyle.setFont(boldRedFont);

		// 세 번째 시트의 두 번째 열을 순회하며 제품 코드를 조회합니다.
		for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) { // 두 번째 행부터 시작 (index 1)
			Row row = sheet.getRow(rowIndex);
			if (row != null) {
				Cell cell = row.getCell(1); // 두 번째 열 (index 1)
				if (cell != null) {
					String productCode = cell.getStringCellValue();
					Optional<Product> productOpt = productRepository.findByProductCode(productCode);

					if (productOpt.isPresent()) {
						Product product = productOpt.get();
						String productRepImageRoad = product.getProductRepImageRoad();
						if (productRepImageRoad != null
								&& (productRepImageRoad.contains("/front/clean/sample/prepare.png")
										|| productRepImageRoad.contains("prepare.png"))) {
							cell.setCellStyle(boldRedStyle);
						}
					}
				}
			}
		}

		// 엑셀 파일을 바이트 배열로 변환하여 반환합니다.
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		workbook.write(outputStream);
		workbook.close();
		return outputStream.toByteArray();
	}

	public Page<Product> getProductsByCriteria(Long tagId, Long colorId, Long middleSortId, Long bigSortId,
			Pageable pageable) {

		Optional<MiddleSort> middleSortOpt = productMiddleSortRepository.findById(middleSortId);

		boolean isMiddleSortAll = middleSortId == 0L
				|| (middleSortOpt.isPresent() && "분류전체".equals(middleSortOpt.get().getName()));
		boolean isTagIdNullOrZero = tagId == null || tagId == 0L;
		boolean isColorIdNullOrZero = colorId == null || colorId == 0L;

		if (isMiddleSortAll) {
			if (!isTagIdNullOrZero && !isColorIdNullOrZero) {
				return productRepository.findByTagColorAndBigSort(tagId, colorId, bigSortId, pageable);
			} else if (!isTagIdNullOrZero) {
				return productRepository.findByTagAndBigSort(tagId, bigSortId, pageable);
			} else if (!isColorIdNullOrZero) {
				return productRepository.findByColorAndBigSort(colorId, bigSortId, pageable);
			} else {
				return productRepository.findByBigSort(bigSortId, pageable);
			}
		} else {
			if (!isTagIdNullOrZero && !isColorIdNullOrZero) {
				return productRepository.findByTagColorAndSorts(tagId, colorId, middleSortId, bigSortId, pageable);
			} else if (!isTagIdNullOrZero) {
				return productRepository.findByTagAndSorts(tagId, middleSortId, bigSortId, pageable);
			} else if (!isColorIdNullOrZero) {
				return productRepository.findByColorAndSorts(colorId, middleSortId, bigSortId, pageable);
			} else {
				return productRepository.findBySorts(middleSortId, bigSortId, pageable);
			}
		}
	}

	public List<Product> getRandomProductsByTag(Product product) {
		List<Long> tagIds = product.getProductTags().stream().map(ProductTag::getId).collect(Collectors.toList());
		return productRepository.findRandomProductsByTag(tagIds, product.getId()).stream().limit(10)
				.collect(Collectors.toList());
	}

	public Product productInsert(ProductDTO dto) throws IllegalStateException, IOException {

		int leftLimit = 48; // numeral '0'
		int rightLimit = 122; // letter 'z'
		int targetStringLength = 10;
		Random random = new Random();
		String generatedString = random.ints(leftLimit, rightLimit + 1)
				.filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97)).limit(targetStringLength)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();

		// 실제 파일 저장 위치 (절대 경로)
		String path = commonPath + "/product/" + productBigSortRepository.findById(dto.getBigSort()).get().getId() + "/"
				+ dto.getProductCode() + "/rep";

		// 파일 resource 로드 url
		String road = "/upload/product/" + productBigSortRepository.findById(dto.getBigSort()).get().getId() + "/"
				+ dto.getProductCode() + "/rep";

		int index = 1;
		if (productRepository.findFirstIndex().isPresent()) {
			index = productRepository.findFirstIndex().get() + 1;
		}
		Product product = new Product();
		product.setName(dto.getProductName());
		product.setProductCode(dto.getProductCode());
		product.setTitle(dto.getProductTitle());
		product.setSubject(dto.getSubject());
		product.setOrder(dto.getOrder());
		product.setProductSign(true);
		product.setUnit("EA");
		product.setBigSort(productBigSortRepository.findById(dto.getBigSort()).get());
		product.setMiddleSort(productMiddleSortRepository.findById(dto.getMiddleSort()).get());
		product.setProductIndex(index);

		List<ProductColor> colors = new ArrayList<>();
		if (dto.getColors() != null) {
			for (Long id : dto.getColors()) {
				colors.add(productColorRepository.findById(id).get());
			}
			product.setProductColors(colors);
		}

		List<ProductOption> options = new ArrayList<>();
		if (dto.getOptions() != null) {
			for (Long id : dto.getOptions()) {
				options.add(productOptionRepository.findById(id).get());
			}
			product.setProductOptions(options);
		}

		List<ProductTag> tags = new ArrayList<>();
		if (dto.getTags() != null) {
			for (Long id : dto.getTags()) {
				tags.add(productTagRepository.findById(id).get());
			}
			product.setProductTags(tags);
		}

		List<ProductSize> sizes = new ArrayList<>();
		if (dto.getSizes() != null) {
			for (Long id : dto.getSizes()) {
				sizes.add(productSizeRepository.findById(id).get());
			}
			product.setProductSizes(sizes);
		}

		String contentType = dto.getProductImage().getContentType();
		String originalFileExtension = "";

		if (ObjectUtils.isEmpty(contentType)) {
			return null;
		} else {
			if (contentType.contains("image/jpeg")) {
				originalFileExtension = ".jpg";
			} else if (contentType.contains("image/png")) {
				originalFileExtension = ".png";
			}
		}
		String productImageName = generatedString + originalFileExtension;
		String productImagePath = path + "representative/" + productImageName;
		String productImageRoad = road + "representative/" + productImageName;

		// Create directory if it does not exist
		File directory = new File(path + "rep/");
		if (!directory.exists()) {
			boolean dirsCreated = directory.mkdirs();
			System.out.println("Directories created: " + dirsCreated);
		}

		// Check if directory exists and is writable
		if (directory.exists() && directory.isDirectory() && directory.canWrite()) {
			File productImageSaveFile = new File(productImagePath);
			dto.getProductImage().transferTo(productImageSaveFile);

			product.setProductRepImageOriginalName(dto.getProductImage().getOriginalFilename());
			product.setProductRepImageName(productImageName);
			product.setProductRepImageExtension(originalFileExtension);
			product.setProductRepImageRoad(productImageRoad);
			product.setProductRepImagePath(productImagePath);

			Product savedProduct = productRepository.save(product);

			return savedProduct;
		} else {
			throw new IOException(
					"Cannot create directories or no write permission for the path: " + directory.getAbsolutePath());
		}
	}

	public Page<Product> findAllByBigMiddleTagColor(Pageable pageable, Long id) {
		Page<Product> products = productRepository.findAllByBigSortOrderByProductIndexAsc(pageable,
				productBigSortRepository.findById(id).get());

		return products;
	}

	public List<Product> getProductsByBigSort(String category) {
		BigSort bigSort = productBigSortRepository.findByName(category);
		return productRepository.findAllByBigSort(bigSort);
	}

	public Map<String, List<?>> getProductOptions(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));
		Map<String, List<?>> options = new HashMap<>();
		options.put("sizes", product.getProductSizes());
		options.put("colors", product.getProductColors());
		return options;
	}
	
	// 제품 이름으로 제품 상세 정보 조회
    @Transactional(readOnly = true)
    public Product getProductDetailsByName(String productName) {
        return productRepository.findByName(productName);
    }
}
