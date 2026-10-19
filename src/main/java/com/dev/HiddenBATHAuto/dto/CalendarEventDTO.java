package com.dev.HiddenBATHAuto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CalendarEventDTO {
    
	private String date; // "yyyy-MM-dd"
    private int asCount;
    private int taskCount;
    private int orderCount;
    private int changeCount;
    public CalendarEventDTO(String date, int asCount, int taskCount) {
        this(date, asCount, taskCount, taskCount, 0);
    }
}

