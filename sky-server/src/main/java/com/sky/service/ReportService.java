package com.sky.service;

import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {
    //根据时间区间统计营业额
    TurnoverReportVO getTurnover(LocalDate begin, LocalDate end);

    //根据时间区间统计用户数量
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);
}
