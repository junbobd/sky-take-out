package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkSpaceServiceImpl implements WorkSpaceService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    public WorkSpaceServiceImpl(OrderMapper orderMapper, UserMapper userMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    //根据时间段统计数据
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        /**
         * 营业额：当日已完成订单的总金额
         * 有效订单：当日已完成订单的数量
         * 订单完成率：有效订单数 / 总订单数
         * 平均客单价：营业额 / 有效订单数
         * 新增用户：当日新增用户的数量
         */

        Map map =new HashMap<>();
        map.put("begin",begin);
        map.put("end",end);
        
        //查询总订单数
        Integer totalOrderCount = orderMapper.countByMap(map);

        map.put("status", Orders.COMPLETED);
        //营业额
        Double turnover = orderMapper.sumByMap(map);
        turnover = turnover == null?0.0:turnover;

        //有效订单数
        Integer validOrderCount = orderMapper.countByMap(map);
        //平均客单价
        Double unitPrice=0.0;
        //订单完成率
        Double orderCompletionRate=0.0;

        if(totalOrderCount!=0 &&validOrderCount!=0){
            orderCompletionRate=validOrderCount.doubleValue()/totalOrderCount;
            unitPrice=turnover/validOrderCount;
        }

        //新增用户
        Integer newUsers = userMapper.countByMap(map);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();

    }

    //查询订单管理数据
    public OrderOverViewVO getOrderOverView() {
        Map map = new HashMap<>();
        map.put("begin",LocalDateTime.now().with(LocalTime.MIN));

        //待接单
        map.put("status",Orders.TO_BE_CONFIRMED);
        Integer waitngOrders = orderMapper.countByMap(map);

        //待派送
        map.put("status", Orders.CONFIRMED);
        Integer deliveredOrders = orderMapper.countByMap(map);

        //已完成
        map.put("status",Orders.COMPLETED);
        Integer completedOrders = orderMapper.countByMap(map);

        //已取消
        map.put("status",Orders.CANCELLED);
        Integer cancelledOrders = orderMapper.countByMap(map);

        //全部订单
        map.put("status",null);
        Integer allOrders = orderMapper.countByMap(map);

        return OrderOverViewVO.builder()
                .waitingOrders(waitngOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    //查询菜品总览
    public DishOverViewVO getDishOverView() {
        Map map = new HashMap<>();
        map.put("status", StatusConstant.ENABLE);
        Integer sold = dishMapper.countByMap(map);//起售菜品数量

        map.put("status",StatusConstant.DISABLE);
        Integer discountinued= dishMapper.countByMap(map);//未起售菜品数量

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discountinued)
                .build();
    }


    //查询套餐总览
    public SetmealOverViewVO getSetmealOverView() {
        Map map = new HashMap<>();
        map.put("status", StatusConstant.ENABLE);
        Integer sold = setmealMapper.countByMap(map);//起售菜品数量

        map.put("status",StatusConstant.DISABLE);
        Integer discountinued= setmealMapper.countByMap(map);//未起售菜品数量

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discountinued)
                .build();
    }
}
