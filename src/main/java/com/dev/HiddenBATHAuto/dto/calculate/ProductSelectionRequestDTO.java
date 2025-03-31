package com.dev.HiddenBATHAuto.dto.calculate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSelectionRequestDTO {

	private CategoryDTO category;
	private Long middleSort;
	private Long product;
	private String form;
	private Integer color;
	private String size;

	private String formofwash;
	private String sortofunder;
	private String sortofdogi;
	private Integer numberofwash;
	private String positionofwash;
	private String colorofmarble;

	private String door;
	private String formofdoor_other;
	private String formofdoor_slide;
	private Integer numberofdoor;
	private Integer numberofdrawer;
	private String doorDirection;

	private String maguri;
	private String directionofmaguri;
	private Integer sizeofmaguri;
	private String hole;

	private String handle;
	private String handletype;
	private String dolche_color;
	private String d195_color;
	private String half_color;
	private String circle_color;
	private String d310_color;

	private String board;
	private String directionofboard;

	private String led;
	private String ledPosition;
	private String ledColor;

	private String mirrorDirection;
	private String outletPosition;
	private String dryPosition;
	private String tissuePosition;

}
