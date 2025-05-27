package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.calculate.low.BasePrice;
import com.dev.HiddenBATHAuto.model.calculate.low.MarbleLengthPrice;
import com.dev.HiddenBATHAuto.model.calculate.low.MarbleType;
import com.dev.HiddenBATHAuto.model.calculate.low.OptionPrice;
import com.dev.HiddenBATHAuto.model.calculate.low.SeriesPrice;
import com.dev.HiddenBATHAuto.model.calculate.low.WashPrice;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.Series;
import com.dev.HiddenBATHAuto.repository.caculate.low.BasePriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.MarbleLengthPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.MarbleTypeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.OptionPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.SeriesPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.WashPriceRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LowCalculateService {

	private final ProductRepository productRepository;
	private final BasePriceRepository basePriceRepository;
	private final ProductSeriesRepository productSeriesRepository;
	private final SeriesPriceRepository seriesPriceRepository;
	private final WashPriceRepository washPriceRepository;
	private final MarbleTypeRepository marbleTypeRepository;
	private final MarbleLengthPriceRepository marbleLengthPriceRepository;
	private final OptionPriceRepository optionPriceRepository;
	private final MarbleLowCalculateService marbleLowCalculateService;
	 
	 
	public Map<String, Object> calculate(Map<String, Object> selection) {
		int mainPrice = 0;
		int variablePrice = 0;
		List<String> reasons = new ArrayList<>();

		Object middleSort = selection.get("middleSort");
		String middleSortStr = middleSort != null ? String.valueOf(middleSort) : "";

		if (!"12".equals(middleSortStr)) {
			Long productId = Long.parseLong(String.valueOf(selection.get("product")));
			Product product = productRepository.findById(productId).orElse(null);
			if (product == null) {
				reasons.add("제품 정보 조회 실패");
				return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
			}
			
			int basicWidth = product.getBasicWidth();
			int basicHeight = product.getBasicHeight();
			int basicDepth = product.getBasicDepth();
			
			String productName = product.getName();
			reasons.add("✔️ 제품명: " + productName);
	        reasons.add("✔️ 제품 기준 사이즈: W" + basicWidth + ", H" + basicHeight + ", D" + basicDepth);
			
			String sizeStr = (String) selection.get("size");
			int width = 0, height = 0, depth = 0;
			if (sizeStr != null) {
			    try {
			        String[] parts = sizeStr.split(",");
			        width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
			        height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
			        depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));

			        reasons.add("✔️ 입력 사이즈: W" + width + ", H" + height + ", D" + depth);

			        int targetWidth = mapWidthToStandard(width);
			        BasePrice base = basePriceRepository.findByStandardWidth(targetWidth);

			        if (base != null) {
			            int baseValue = 0;
			            String depthRangeUsed = "";

			            if (depth <= 460) {
			                baseValue = base.getPrice460();
			                depthRangeUsed = "≤ 460 (price460)";
			            } else if (depth <= 560) {
			                baseValue = base.getPrice560();
			                depthRangeUsed = "≤ 560 (price560)";
			            } else if (depth <= 620) {
			                baseValue = base.getPrice620();
			                depthRangeUsed = "≤ 620 (price620)";
			            } else {
			                baseValue = base.getPrice700();
			                depthRangeUsed = "> 620 (price700)";
			            }

			            reasons.add("✔️ 기준 너비 매핑: " + width + " → " + targetWidth);
			            reasons.add("✔️ 깊이에 따라 적용된 가격 (" + depthRangeUsed + "): " + baseValue + "원");

			            if (height > 800) {
			                baseValue += 35000;
			                reasons.add("✔️ 높이 > 800 → 35,000원 추가");
			            }

			            mainPrice += baseValue;
			            reasons.add("✔️ 최종 기본 가격 적용: " + baseValue + "원");
			        } else {
			            reasons.add("❌ 기준 너비(" + targetWidth + ")에 해당하는 기본 가격 데이터 없음");
			        }

			    } catch (Exception e) {
			        reasons.add("❌ 사이즈 파싱 실패 → 입력 문자열: " + sizeStr);
			    }
			}

			Long seriesId = Long.parseLong(middleSortStr);
			Series series = productSeriesRepository.findById(seriesId).orElse(null);
			if (series != null) {
				String seriesName = series.getName();
				if (List.of("프리미엄", "라운드", "슬라이드").contains(seriesName)) {
					int sw = mapWidthToStandard(width);
					SeriesPrice sp = seriesPriceRepository.findByStandardWidth(sw);
					if (sp != null) {
						int additional = switch (seriesName) {
						case "프리미엄" -> sp.getPremium();
						case "라운드" -> sp.getRound();
						case "슬라이드" -> sp.getSlide();
						default -> 0;
						};
						mainPrice += additional;
						reasons.add("시리즈(" + seriesName + ") 기준 너비 " + sw + " 적용 추가금: " + additional);
					}
				}
			}

			String formofwash = (String) selection.get("formofwash");
			if (formofwash != null) {
				if (formofwash.equals("under") || formofwash.equals("dogi")) {
				    Object sortIdObj = selection.get("sortof" + formofwash);
				    Integer numberOf = tryParseInt(selection.get("numberofwash"));

				    if (sortIdObj != null && numberOf != null) {
				        try {
				            Long originalSortId = Long.parseLong(String.valueOf(sortIdObj));
				            Long sortId = (originalSortId == 4 || originalSortId == 5 || originalSortId == 6 || originalSortId == 7)
				                ? 4L
				                : originalSortId;

				            WashPrice wp = washPriceRepository.findById(sortId).orElse(null);
				            if (wp != null) {
				                int washPrice = (wp.getBasePrice() + wp.getAdditionalFee()) * numberOf;
				                mainPrice += washPrice;
				                reasons.add("세면대 ID: " + originalSortId + " (조회 ID: " + sortId + "), 수량: " + numberOf + ", 총 금액: " + washPrice);
				            } else {
				                reasons.add("세면대 ID로 WashPrice 조회 실패: " + sortId);
				            }
				        } catch (NumberFormatException e) {
				            reasons.add("세면대 ID 파싱 실패: " + sortIdObj);
				        }
				    } else {
				        reasons.add("세면대 ID 또는 수량 없음");
				    }
				}
				if (List.of("marble", "under", "dogi").contains(formofwash)) {
					String marbleName = String.valueOf(selection.get("colorofmarble"));
					MarbleType mt = marbleTypeRepository.findByMarbleName(marbleName);
					if (mt != null) {
						int unit = mt.getUnitPrice();
						int add = (unit == 2 ? width * 50 : (unit == 3 ? width * 130 : 0));
						mainPrice += add;
						reasons.add("대리석 색상: " + marbleName + ", 단가등급: " + unit + ", 추가금: " + add);
					}
				}
			}

			if ("add".equals(selection.get("maguri"))) {
				Integer sizeofmaguri = tryParseInt(selection.get("sizeofmaguri"));
				if (sizeofmaguri != null && sizeofmaguri > 150) {
					String dir = (String) selection.get("directionofmaguri");
					int totalLength = calculateLengthByDirection(dir, width, depth);
					int sw = mapWidthToStandard(totalLength);
					MarbleLengthPrice mlp = marbleLengthPriceRepository.findByStandardWidth(sw);
					MarbleType mt = marbleTypeRepository
							.findByMarbleName(String.valueOf(selection.get("colorofmarble")));
					if (mlp != null && mt != null) {
						int base = switch (mt.getUnitPrice()) {
						case 2 -> mlp.getPrice2();
						case 3 -> mlp.getPrice3();
						default -> mlp.getPrice1();
						};
						int maguriTotal = base + mlp.getAdditionalFee();
						mainPrice += maguriTotal;
						reasons.add("마구리 총 길이: " + totalLength + "mm, 기준: " + sw + "mm → 추가금: " + maguriTotal);
					}
				} else {
					reasons.add("마구리 사이즈 150 이하이므로 추가금 없음");
				}
			} else {
				reasons.add("마구리 추가 선택 안됨");
			}

			if ("add".equals(selection.get("board"))) {
				String dir = (String) selection.get("directionofboard");
				int length = calculateLengthByDirection(dir, width, depth);
				int add = length * 20;
				mainPrice += add;
				reasons.add("걸레받이 방향: " + dir + ", 길이: " + length + "mm, 추가금: " + add);
			} else {
				reasons.add("걸레받이 선택 안됨");
			}

			if ("add".equals(selection.get("led"))) {
				String pos = String.valueOf(selection.get("ledPosition"));
				int ledCount = ("5".equals(pos)) ? 2 : 1;
				OptionPrice op = optionPriceRepository.findByOptionName("하부LED");
				if (op != null) {
					int ledPrice = ledCount * op.getPrice();
					mainPrice += ledPrice;
					reasons.add("LED 수량: " + ledCount + "개, 단가: " + op.getPrice() + " → 총 추가금: " + ledPrice);
				}
			} else {
				reasons.add("LED 선택 안됨");
			}
			
			mainPrice += addOptionFromOptionTable(selection, "outletPosition", "콘센트", reasons);
			mainPrice += addOptionFromOptionTable(selection, "dryPosition", "드라이걸이", reasons);
			mainPrice += addOptionFromOptionTable(selection, "tissuePosition", "티슈홀캡", reasons);
			
			String door = String.valueOf(selection.get("door"));
	        if ("add".equals(door)) {
	            String formSlide = String.valueOf(selection.get("formofdoor_slide"));
	            String formOther = String.valueOf(selection.get("formofdoor_other"));
	            String form = (!"null".equals(formSlide) && formSlide != null) ? formSlide : formOther;
	            reasons.add("문 추가됨 (형태: " + form + ", 수량: " + selection.get("numberofdoor") + ")");
	        } else {
	            reasons.add("문 추가 없음");
	        }

	        String handle = String.valueOf(selection.get("handle"));
	        if ("add".equals(handle)) {
	            reasons.add("손잡이 추가됨 (종류: " + selection.get("handletype") + ")");
	        } else {
	            reasons.add("손잡이 추가 없음");
	        }

	        String hole = String.valueOf(selection.get("hole"));
	        if ("add".equals(hole)) {
	            reasons.add("상판 타공 있음");
	        } else {
	            reasons.add("상판 타공 없음");
	        }
			
			variablePrice = mainPrice;
		}else if("12".equals(middleSortStr)){
			return marbleLowCalculateService.calculateForMarbleLow(selection);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("mainPrice", mainPrice);
		result.put("variablePrice", variablePrice);
		result.put("reasons", reasons);
		return result;
	}

	 private int mapWidthToStandard(int width) {
        return ((width + 99) / 100) * 100;
    }

	private Integer tryParseInt(Object value) {
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (Exception e) {
			return null;
		}
	}

	private int calculateLengthByDirection(String direction, int width, int depth) {
		return switch (direction) {
		case "front" -> width;
		case "front_left", "front_right" -> width + depth;
		case "front_left_right" -> width + 2 * depth;
		default -> 0;
		};
	}

	private int addOptionFromOptionTable(Map<String, Object> selection, String key, String optionName,
			List<String> reasons) {
		Object val = selection.get(key);
		if (val == null || "7".equals(String.valueOf(val))) {
			reasons.add(optionName + " 선택 안됨");
			return 0;
		}
		OptionPrice op = optionPriceRepository.findByOptionName(optionName);
		if (op != null) {
			reasons.add(optionName + " 추가됨: " + op.getPrice());
			return op.getPrice();
		}
		reasons.add(optionName + " 가격 조회 실패");
		return 0;
	}
}
