package com.dev.HiddenBATHAuto.service.auth;

import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegionExcelService {

    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    public void uploadRegionExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 사용
            int rowCount = sheet.getPhysicalNumberOfRows();

            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String provinceName = getCellValue(row.getCell(0));
                String cityName = getCellValue(row.getCell(1));
                String districtName = getCellValue(row.getCell(2));

                if (provinceName == null || provinceName.isBlank()) continue;

                // Province 저장
                Province province = provinceRepository.findByName(provinceName)
                	    .orElseGet(() -> {
                	        Province p = new Province();
                	        p.setName(provinceName);
                	        return provinceRepository.save(p);
                	    });

                // City 저장 (있을 때만)
                City city = null;
                if (cityName != null && !cityName.isBlank()) {
                    city = cityRepository.findByNameAndProvince(cityName, province)
                        .orElseGet(() -> {
                            City c = new City();
                            c.setName(cityName);
                            c.setProvince(province);
                            return cityRepository.save(c);
                        });
                }

                // District 저장 (district 이름이 있을 경우만)
                if (districtName != null && !districtName.isBlank()) {
                    boolean exists = districtRepository.findByNameAndProvinceAndCity(districtName, province, city).isPresent();
                    if (!exists) {
                        District district = new District();
                        district.setName(districtName);
                        district.setProvince(province);
                        district.setCity(city); // city는 null 가능
                        districtRepository.save(district);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("엑셀 업로드 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((int) cell.getNumericCellValue());
        return null;
    }
}
