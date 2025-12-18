package com.dev.HiddenBATHAuto.service.excel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.constant.KakaoAddressClient;
import com.dev.HiddenBATHAuto.dto.address.AddressPickResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyExcelConvertService {

	private final KakaoAddressClient kakaoAddressClient;

	public byte[] convertToExcelBytes(MultipartFile file) throws Exception {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
		}

		try (InputStream in = file.getInputStream();
				Workbook inWb = WorkbookFactory.create(in);
				SXSSFWorkbook outWb = new SXSSFWorkbook(200);
				ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

			Sheet inSheet = inWb.getSheetAt(0);
			Sheet outSheet = outWb.createSheet("converted");

			SXSSFSheet sx = (SXSSFSheet) outSheet;
			sx.trackAllColumnsForAutoSizing();

			CellStylePack styles = createStyles(outWb);

			final String[] headers = { "코드", "거래처명(원본)", "거래처명(지역삭제)", "유형", "사업자번호", "대표자명", "업태", "업종", "주소(원본)",
					"우편번호", "도", "시", "구", "도로명주소", "지번주소", "상세주소", "전화", "핸드폰", "팩스", "이메일" };
			writeHeader(outSheet, headers);

			DataFormatter fmt = new DataFormatter();
			int outRowIdx = 1;

			Iterator<Row> it = inSheet.iterator();
			if (it.hasNext())
				it.next(); // 입력 헤더 스킵

			while (it.hasNext()) {
				Row row = it.next();

				String codeIn = getCell(row, 0, fmt);
				String companyIn = getCell(row, 1, fmt);
				String typeIn = getCell(row, 2, fmt);
				String brnIn = getCell(row, 3, fmt);
				String ceoIn = getCell(row, 4, fmt);
				String bizTypeIn = getCell(row, 5, fmt);
				String bizItemIn = getCell(row, 6, fmt);
				String addressIn = getCell(row, 7, fmt);
				String telIn = getCell(row, 8, fmt);
				String phoneIn = getCell(row, 9, fmt);
				String faxIn = getCell(row, 10, fmt);
				String emailIn = getCell(row, 11, fmt);

				String companyRegionRemoved = stripRegionPrefix(companyIn);

				String zip = "", doName = "", siName = "", guName = "";
				String roadAddress = "", jibunAddress = "", detailAddr = "";

				if (!isBlank(addressIn)) {
					AddressPickResult res;
					try {
						res = kakaoAddressClient.resolve(addressIn);
					} catch (Exception ex) {
						// ✅ 한 행에서 실패해도 전체 변환이 죽지 않게
						res = AddressPickResult.empty("");
					}

					if (res != null && res.isSuccess()) {
						zip = safe(res.getZip());
						doName = safe(res.getDoName());
						siName = safe(res.getSiName());
						guName = safe(res.getGuName());
						roadAddress = safe(res.getRoadAddress());
						jibunAddress = safe(res.getJibunAddress());
						detailAddr = safe(res.getDetailAddress());
					} else {
						detailAddr = (res == null) ? "" : safe(res.getDetailAddress());
					}
				}

				Row out = outSheet.createRow(outRowIdx++);

				writeOrNullRed(out, 0, codeIn, styles);
				writeOrNullRed(out, 1, companyIn, styles);
				writeOrNullRed(out, 2, companyRegionRemoved, styles);
				writeOrNullRed(out, 3, typeIn, styles);
				writeOrNullRed(out, 4, brnIn, styles);
				writeOrNullRed(out, 5, ceoIn, styles);
				writeOrNullRed(out, 6, bizTypeIn, styles);
				writeOrNullRed(out, 7, bizItemIn, styles);

				writeOrNullRed(out, 8, addressIn, styles);

				writeOrNullRed(out, 9, zip, styles);
				writeOrNullRed(out, 10, doName, styles);
				writeOrNullRed(out, 11, siName, styles);
				writeOrNullRed(out, 12, guName, styles);
				writeOrNullRed(out, 13, roadAddress, styles);
				writeOrNullRed(out, 14, jibunAddress, styles);
				writeOrNullRed(out, 15, detailAddr, styles);
				writeOrNullRed(out, 16, telIn, styles);
				writeOrNullRed(out, 17, phoneIn, styles);
				writeOrNullRed(out, 18, faxIn, styles);
				writeOrNullRed(out, 19, emailIn, styles);
			}

			for (int c = 0; c <= 19; c++)
				outSheet.autoSizeColumn(c);

			outWb.write(baos);
			outWb.dispose();
			return baos.toByteArray();
		}
	}

	private static class CellStylePack {
		private final CellStyle nullRedStyle;

		CellStylePack(CellStyle nullRedStyle) {
			this.nullRedStyle = nullRedStyle;
		}
	}

	private static CellStylePack createStyles(Workbook wb) {
		CellStyle nullStyle = wb.createCellStyle();
		Font redFont = wb.createFont();
		redFont.setColor(IndexedColors.RED.getIndex());
		nullStyle.setFont(redFont);
		return new CellStylePack(nullStyle);
	}

	private static void writeHeader(Sheet sh, String[] headers) {
		Row hr = sh.createRow(0);
		for (int i = 0; i < headers.length; i++) {
			Cell c = hr.createCell(i, CellType.STRING);
			c.setCellValue(headers[i]);
		}
	}

	private static String getCell(Row r, int col, DataFormatter fmt) {
		if (r == null)
			return "";
		Cell c = r.getCell(col);
		if (c == null)
			return "";
		return fmt.formatCellValue(c).trim();
	}

	private static void writeOrNullRed(Row r, int col, String v, CellStylePack styles) {
		Cell c = r.createCell(col, CellType.STRING);
		if (isBlank(v)) {
			c.setCellValue("NULL");
			c.setCellStyle(styles.nullRedStyle);
		} else {
			c.setCellValue(v.trim());
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}

	private static String stripRegionPrefix(String v) {
		if (v == null)
			return null;
		String s = v.trim();
		if (s.isEmpty())
			return null;

		int idx = s.indexOf('/');
		if (idx < 0)
			return s;

		String after = s.substring(idx + 1).trim();
		if (after.isEmpty())
			return s;
		return after;
	}
}