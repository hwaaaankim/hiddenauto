package com.dev.HiddenBATHAuto.service.product;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductFile;
import com.dev.HiddenBATHAuto.repository.repository.ProductFileRepository;

@Service
public class ProductFileService {

	@Autowired
	ProductFileRepository productFileRepository;
	
	@Value("${spring.upload.env}")
	private String env;
	
	@Value("${spring.upload.path}")
	private String commonPath;
	
	public Boolean fileDelete(
			Long id
			) {
		List<ProductFile> files = productFileRepository.findAllByProductId(id);
		for(ProductFile p : files) {
			File pFile = new File(p.getProductFilePath());
			if(!pFile.delete()) {
				return false;
			}
		}
		
		return true;
	}
	
	public String fileUpload(
			Product product,
			List<MultipartFile> productFiles
			) throws IllegalStateException, IOException {
		
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String current_date = simpleDateFormat.format(new Date());

        // 실제 파일 저장 위치
  		String path = commonPath 
  				+ "/product/"
  				+ product.getBigSort().getId() 
  				+ "/"
  				+ product.getMiddleSort().getId()
  				+ "/"
  				+ product.getProductCode()
  				+ "/"
  				+ current_date; 
  		// 파일 resource 로드 url
  		String road = "/upload/product/"
  				+ product.getBigSort().getId() 
  				+ "/"
  				+ product.getMiddleSort().getId()
  				+ "/"
  				+ product.getProductCode()
  				+ "/"
  				+ current_date; 
        
        int leftLimit = 48; // numeral '0'
		int rightLimit = 122; // letter 'z'
		int targetStringLength = 10;
		Random random = new Random();
		
        for(MultipartFile file : productFiles) {
        	if(!file.isEmpty()) {
        		String generatedString = random.ints(leftLimit,rightLimit + 1)
      				  .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
      				  .limit(targetStringLength)
      				  .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      				  .toString();
        		ProductFile f = new ProductFile();
        		f.setProductId(product.getId());
            	String contentType = file.getContentType();
                String originalFileExtension = "";
                // 확장자 명이 없으면 이 파일은 잘 못 된 것이다
                if (ObjectUtils.isEmpty(contentType)){
                    return "NONE";
                }else {
        			if (contentType.contains("image/jpeg")) {
        				originalFileExtension = ".jpg";
        			} else if (contentType.contains("image/png")) {
        				originalFileExtension = ".png";
        			} else if (contentType.contains("image/gif")) {
        				originalFileExtension = ".gif";
        			} else if (contentType.contains("application/pdf")) {
        				originalFileExtension = ".pdf";
        			} else if (contentType.contains("application/x-zip-compressed")) {
        				originalFileExtension = ".zip";
        			} else if (contentType
        					.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
        				originalFileExtension = ".xlsx";
        			} else if (contentType
        					.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
        				originalFileExtension = ".docx";
        			} else if (contentType.contains("text/plain")) {
        				originalFileExtension = ".txt";
        			} else if (contentType.contains("image/x-icon")) {
        				originalFileExtension = ".ico";
        			} else if (contentType.contains("application/haansofthwp")) {
        				originalFileExtension = ".hwp";
        			}
                }
                String productFileName = generatedString + originalFileExtension;
				String productFilePath = path + "/files/" + productFileName;
				String productFileRoad = road + "/files/" + productFileName;
				String productFileSavePath = productFilePath;
				File productFileSaveFile = new File(productFileSavePath);	
				if (!productFileSaveFile.exists()) {
					productFileSaveFile.mkdirs();
				}
                file.transferTo(productFileSaveFile);
                f.setProductFileOriginalName(file.getOriginalFilename());
                f.setProductFileExtension(originalFileExtension);
                f.setProductFilePath(productFilePath);
                f.setProductFileRoad(productFileRoad);
                f.setProductFileName(productFileName);
                f.setProductFileDate(new Date());
                f.setSign(true);
                productFileRepository.save(f);
            }
        }
        
        return "success";
	}
	
}
























