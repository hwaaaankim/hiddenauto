package com.dev.HiddenBATHAuto.model.calculate.mirror;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "tb_mirror_series_six_led")
public class MirrorSeriesSixLed {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "standard_width")
	private int standardWidth;

	@Column(name = "price200")
    private int price200;
	@Column(name = "price300")
	private int price300;
	@Column(name = "price400")
	private int price400;
	@Column(name = "price500")
	private int price500;
	@Column(name = "price600")
	private int price600;
	@Column(name = "price700")
	private int price700;
	@Column(name = "price800")
	private int price800;
	@Column(name = "price900")
	private int price900;
	@Column(name = "price1000")
	private int price1000;
	@Column(name = "price1100")
	private int price1100;
	@Column(name = "price1200")
	private int price1200;
	@Column(name = "price1300")
	private int price1300;
	@Column(name = "price1400")
	private int price1400;
	@Column(name = "price1500")
	private int price1500;
	@Column(name = "price1600")
	private int price1600;
	@Column(name = "price1700")
	private int price1700;
	@Column(name = "price1800")
	private int price1800;
}
