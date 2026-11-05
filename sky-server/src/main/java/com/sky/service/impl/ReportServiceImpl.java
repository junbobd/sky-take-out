package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    //根据时间区间统计营业额
    public TurnoverReportVO getTurnover(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList=new ArrayList<>();//时间
        dateList.add(begin);

        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double>turnoverList=new ArrayList<>();//每天的营业额

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("status", Orders.COMPLETED);
            map.put("begin",beginTime);
            map.put("end",endTime);

            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //数据封装
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();

        return turnoverReportVO;

    }

    //根据时间区间统计用户数量
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate>dateList=new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Integer>newUserList=new ArrayList<>();
        List<Integer>totalUserList= new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime=LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date,LocalTime.MAX);

            //新增用户数量
            Integer newUser = getUserCount(beginTime,endTime);

            //总用户数量
            Integer totalUser = getUserCount(null,endTime);

            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .build();
    }

    private Integer getUserCount(LocalDateTime beginTime,LocalDateTime endTime){
        Map map = new HashMap();
        map.put("begin",beginTime);
        map.put("end",endTime);
        return userMapper.countByMap(map);
    }
}
