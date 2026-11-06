package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkSpaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkSpaceService workSpaceService;

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


    //根据时间区间统计订单数量
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate>dateList  = new ArrayList<>();
        dateList.add(begin);

        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //每天订单总数集合
        List<Integer>orderCountList=new ArrayList<>();
        //每天有效订单数集合
        List<Integer>validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //查询每天的总订单数
            Integer orderCount = getOrderCount(beginTime,endTime,null);

            //查询每天的有效订单数
            Integer validOrderCount = getOrderCount(beginTime,endTime,Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);

        }

        //时间区间内的总订单数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        //时间区间内的有效订单数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        //订单完成率
        Double orderCompletionRate = 0.0;

        if(totalOrderCount!=0){
            orderCompletionRate = validOrderCount.doubleValue()/totalOrderCount;
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();

    }

    //查询指定时间区间内的销量排名top10
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = begin == null ? null : LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = end == null ? null : LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO>goodsSalesDTOList=orderMapper.getSalesTop10(beginTime,endTime);

        List<String>names = goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names,",");
        List<Integer> numbers = goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers,",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    //导出近30天的运营数据报表
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        //查询概览运营数据，提供给excel模板文件
        BusinessDataVO businessData = workSpaceService.getBusinessData(LocalDateTime.of(begin,LocalTime.MIN),LocalDateTime.of(end,LocalTime.MAX));

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try{
            //基于提供好的模板文件,在内存中创建一个新的excel表格对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);

            //获得excel文件中的一个Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            sheet.getRow(1).getCell(1).setCellValue(begin+"至"+end);

            //获得第四行
            XSSFRow row = sheet.getRow(3);

            //获取单元格
            row.getCell(2).setCellValue(businessData.getTurnover());//写入营业额
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());//写入完成率
            row.getCell(6).setCellValue(businessData.getNewUsers());//写入新增用户数量

            //获得第五行
            row = sheet.getRow(4);
            //获取单元格
            row.getCell(2).setCellValue(businessData.getValidOrderCount());//写入有效订单数
            row.getCell(4).setCellValue(businessData.getUnitPrice());//写入平均客单价

            for(int i=0;i<30;i++){
                LocalDate date = begin.plusDays(i);

                //准备明细数据
                businessData = workSpaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN),LocalDateTime.of(date,LocalTime.MAX));

                //获取行
                row = sheet.getRow(7+i);

                row.getCell(1).setCellValue(date.toString());//写入日期
                row.getCell(2).setCellValue(businessData.getTurnover());//写入营业额
                row.getCell(3).setCellValue(businessData.getValidOrderCount());//写入有效订单数
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());//写入订单完成率
                row.getCell(5).setCellValue(businessData.getUnitPrice());//写入平均客单

            }

            //通过输入流将文件下载到客户端浏览器中
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            //关闭资源
            out.flush();
            out.close();
            excel.close();


        }catch (IOException e){
            e.printStackTrace();
        }
    }





    private Integer getUserCount(LocalDateTime beginTime,LocalDateTime endTime){
        Map map = new HashMap();
        map.put("begin",beginTime);
        map.put("end",endTime);
        return userMapper.countByMap(map);
    }

    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {
        Map map = new HashMap();
        map.put("status",status);
        map.put("begin",beginTime);
        map.put("end",endTime);
        return orderMapper.countByMap(map);
    }


}
