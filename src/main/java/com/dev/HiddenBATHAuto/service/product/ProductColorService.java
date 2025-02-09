package com.dev.HiddenBATHAuto.service.product;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductColor;
import com.dev.HiddenBATHAuto.repository.repository.ProductColorRepository;

@Service
public class ProductColorService {

	@Autowired
	ProductColorRepository productColorRepository;
	
	@Value("${spring.upload.env}")
	private String env;
	
	@Value("${spring.upload.path}")
	private String commonPath;
	
	public String insertProductColor(
			String colorName,
			MultipartFile file
			) throws IOException {
      
        int leftLimit = 48; // numeral '0'
		int rightLimit = 122; // letter 'z'
		int targetStringLength = 10;
		
		Random random = new Random();
		String generatedString = random.ints(leftLimit,rightLimit + 1)
				  .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
				  .limit(targetStringLength)
				  .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
				  .toString();
        
        String path = commonPath + "/color/";
        String road = "/administration/upload/color/";
        
        File fileFolder = new File(path);
        if(!fileFolder.exists()) {
        	fileFolder.mkdirs();
        }
        
        String contentType = file.getContentType();
        String originalFileExtension = "";
        
        if (ObjectUtils.isEmpty(contentType)){
            return "NONE";
        }else{
            if(contentType.contains("image/jpeg")){
                originalFileExtension = ".jpg";
            }
            else if(contentType.contains("image/png")){
                originalFileExtension = ".png";
            }
        }
        
        String fileName = generatedString + originalFileExtension;
        ProductColor c = new ProductColor();
		fileFolder = new File(path + fileName);
		file.transferTo(fileFolder);
		
		c.setProductColorPath(path + fileName);
        c.setProductColorRoad(road + fileName);
        c.setProductColorSubject(colorName);
        productColorRepository.save(c);
        
        return "success";
	}
	
	public void deleteProductColor(Long[] id) {
		for(Long i : id) {
			File colorFile = new File(productColorRepository.findById(i).get().getProductColorPath());
			colorFile.delete();
			productColorRepository.deleteById(i);
		}
	}
	
}

















