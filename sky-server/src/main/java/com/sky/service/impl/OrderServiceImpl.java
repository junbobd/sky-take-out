package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.config.WebSocketConfiguration;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.BaseNumberUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WebSocketServer webSocketServer;


    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    //用户下单
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //异常情况的处理：收货地址为空、超出配送范围、购物车为空

        //收货地址为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //获取当前用户id
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        //查询当前用户的购物车数据
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        //购物车为空
        if(shoppingCartList==null||shoppingCartList.size()==0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));//利用时间戳创建订单号
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);//设置订单状态为待付款
        order.setPayStatus(Orders.UN_PAID);//设置支付方式默认为微信
        order.setOrderTime(LocalDateTime.now());

        //向订单表插入一条数据
        orderMapper.insert(order);//通过xml文件 获得了order的id值（id是自增且由系统自动分配的），order的id在后面要赋给orderdetail

        //由购物车数据 得出 订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }

        //向明细表插入n条数据,具体点了什么菜不放在order表里，放在明细表order_detail里，二者通过order的id 关联
        orderDetailMapper.insertBatch(orderDetailList);

        //清理购物车中的数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
        return orderSubmitVO;

    }



    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // ==================== 【跳过微信支付】修改开始 ====================
        // 原因：没有微信支付商户号，无法调用微信支付接口
        // 做法：注释掉微信支付相关代码，直接调用 paySuccess 修改订单状态，模拟支付成功
        // 恢复：删除本段新增代码，放开下方被注释的原代码即可

        //新增：校验订单是否存在、是否已支付（原来由微信支付接口返回 ORDERPAID 判断）
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }

        //原代码：调用微信支付接口（已注释）
        /*
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
        */

        //新增：直接调用支付成功方法，订单状态改为待接单、支付状态改为已支付
        paySuccess(ordersPaymentDTO.getOrderNumber());
        //新增：返回空的支付参数（小程序端已不再调用 wx.requestPayment）
        return new OrderPaymentVO();
        // ==================== 【跳过微信支付】修改结束 ====================
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        //当前登录用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        //新添websocket
        Map map = new HashMap();
        map.put("type",1);
        map.put("orderId",orders.getId());
        map.put("content","订单号"+outTradeNo);

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    //用户端订单分页查询
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        //设置分页
        PageHelper.startPage(pageNum,pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        //分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list=new ArrayList<>();

        //查询出订单明细，并封装入OrderVO进行响应
         if(page!=null&&page.getTotal()>0){
             for (Orders orders : page) {
                 Long ordersId = orders.getId();

                 //查询订单明细
                 List<OrderDetail> orderDetails  = orderDetailMapper.getByOrderId(ordersId);

                 OrderVO orderVO = new OrderVO();
                 BeanUtils.copyProperties(orders,orderVO);
                 orderVO.setOrderDetailList(orderDetails);

                 list.add(orderVO);
             }
         }

         return new PageResult(page.getTotal(),list);
    }

    //查询订单项详情
    public OrderVO details(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);

        //查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //将订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    //用户取消订单
    public void userCancelById(Long id)  throws Exception {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        //校验订单是否存在
        if(ordersDB==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if(ordersDB.getStatus()>2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        //订单处于接单状态下取消，需要进行退款
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            // ==================== 【跳过微信支付】修改开始 ====================
            // 原因：没有微信支付商户号，调用退款接口会抛异常，导致无法取消订单
            // 做法：注释掉微信退款接口调用，直接视为退款成功
            // 恢复：放开下方被注释的原代码即可
            //原代码：调用微信支付退款接口（已注释）
            /*
            weChatPayUtil.refund(
                    ordersDB.getNumber(),//商户订单号
                    ordersDB.getNumber(),//商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01)//原订单金额
            );
            */
            // ==================== 【跳过微信支付】修改结束 ====================
            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        //更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

    }

    //再来一单
    public void repetition(Long id) {
        //查询当前用户id
        Long userId = BaseContext.getCurrentId();

        //根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        //将订单详情转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            //将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;

        }).collect(Collectors.toList());

        //将购物车对象批量添加到数据库中
        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    //订单搜索
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {

        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(),orderVOList);
    }


    private List<OrderVO> getOrderVOList(Page<Orders>page){
        //需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();

        if(!CollectionUtils.isEmpty(ordersList)){
            for (Orders orders : ordersList) {
                //将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                String orderDishes = getOrderDishesStr(orders);

                //将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }


    //根据订单id获取菜品信息字符串
    private String getOrderDishesStr(Orders orders) {

        //查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        //将该订单对应的所有菜品信息拼接在一起
        return String.join("",orderDishList);
    }


    //各个状态的订单数量统计
    public OrderStatisticsVO statistics() {
        //根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        //将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    //接单
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                //修改：前端接单只传订单id，不传status，状态要由后端设置为已接单
                //原代码：.status(ordersConfirmDTO.getStatus())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }

    //拒单
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception{
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        //订单只有存在且状态为2（待接单）才可以接单
        if(ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if(payStatus== OrderVO.PAID){
            //用户已支付，需要退款
            // ==================== 【跳过微信支付】修改开始 ====================
            // 原因：没有微信支付商户号，调用退款接口会抛异常，导致无法拒单
            // 做法：注释掉微信退款接口调用，只打印日志，视为退款成功
            // 恢复：删除下方新增的日志，放开被注释的原代码即可
            //原代码：调用微信支付退款接口（已注释）
            /*
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01)
            );
            log.info("申请退款：{}",refund);
            */
            //新增：模拟退款成功
            log.info("模拟退款成功，订单号：{}", ordersDB.getNumber());
            // ==================== 【跳过微信支付】修改结束 ====================
        }

        //拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        //修改：id 应取自数据库查出的订单 ordersDB，原代码取的是新建对象自己的 id（为 null），导致拒单不生效
        //原代码：orders.setId(orders.getId());
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        //新增：上方注释写了要更新取消时间，但原代码漏写了
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);

    }


    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        //新增：校验订单是否存在，已完成、已取消的订单不能再取消（原代码没有校验，已完成的订单会被改成已取消）
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (ordersDB.getStatus().equals(Orders.COMPLETED) || ordersDB.getStatus().equals(Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == 1) {
            //用户已支付，需要退款
            // ==================== 【跳过微信支付】修改开始 ====================
            // 原因：没有微信支付商户号，调用退款接口会抛异常，导致管理端无法取消订单
            // 做法：注释掉微信退款接口调用，只打印日志，视为退款成功
            // 恢复：删除下方新增的日志，放开被注释的原代码即可
            //原代码：调用微信支付退款接口（已注释）
            /*
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
            */
            //新增：模拟退款成功
            log.info("模拟退款成功，订单号：{}", ordersDB.getNumber());
            // ==================== 【跳过微信支付】修改结束 ====================
        }

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }


    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }


    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }



    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    //用户催单
    public void reminder(Long id) {
        //查询订单是否存在
        Orders orders = orderMapper.getById(id);
        if(orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //基于WebSocket实现催单
        Map map = new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号"+orders.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

    }

}
