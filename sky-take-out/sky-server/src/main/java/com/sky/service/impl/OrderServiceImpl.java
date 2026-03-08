package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WebSocketServer webSocketServer;

    private Orders orders;


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional  //涉及多表查询，加事务
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        //地址为空，抛异常
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //检查用户的收货地址是否超出配送范围
        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        //购物车为空，抛异常
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        //下单后，设置订单为待付款状态
        orders.setStatus(Orders.PENDING_PAYMENT);
        //下单后，设置支付状态为未支付状态
        orders.setPayStatus(Orders.UN_PAID);
        //下单时间设置
        orders.setOrderTime(LocalDateTime.now());
        //设置订单号，用时间戳来代替
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        //由于之前我们查询过地址簿，而里面有电话号和收货人等信息
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        //重要！设置当前用户id，否则订单不知道属于哪个用户！
        orders.setUserId(userId);

        //存储起来提交的订单
        this.orders = orders;

        //设置地址
        orders.setAddress(addressBook.getDetail());
        //需要进行主键回传，因为下面的订单详细表中的n条数据都需要关联这个订单
        orderMapper.insert(orders);
        //向订单明细表中插入n条数据，订单明细其实就是具体的商品，商品从购物车中获得
        //前面也查询到了购物车信息
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        //使用批量插入而不是一条一条插入，更高效
        orderDetailMapper.insertBatch(orderDetailList);

        //清空用户的购物车（已经下完单了，购物车里的东西就得清空）
        shoppingCartMapper.deleteByUserId(userId);
        //封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
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
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
//        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        //由于个人小程序无法进行微信支付，所以直接跳过微信支付的步骤
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        //更新订单状态为待接单
        Integer orderStatus = Orders.TO_BE_CONFIRMED;
        //更新支付状态为已支付
        Integer orderPaidStatus = Orders.PAID;
        LocalDateTime checkOutTime = LocalDateTime.now();
        orderMapper.updateStatus(orderStatus, orderPaidStatus, checkOutTime, this.orders.getId());
        //通过websocket向管理端浏览器推送信息 type orderId Content
        Map map=new HashMap();
        map.put("type",1);  //1表示来单提醒，2表示用户催单
        map.put("orderId",orders.getId());
        map.put("content","订单号: "+orders.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

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
        //通过websocket向管理端浏览器推送信息 type orderId Content
        Map map=new HashMap();
        map.put("type",1);  //1表示来单提醒，2表示用户催单
        map.put("orderId",ordersDB);
        map.put("content","订单号: "+outTradeNo);
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 查询历史订单
     *
     * @param pageNumber
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult history(Integer pageNumber, Integer pageSize, Integer status) {
        PageHelper.startPage(pageNumber, pageSize);
        //这里需要自己构建查询的DTO，封装用户id，订单状态
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        //涉及订单基本数据和订单的详细数据，多表查询
        //查询订单表，得到订单的分页
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //封装到orderVO来响应
        List<OrderVO> list = new ArrayList<>();
        if (page != null && page.size() > 0) {
            //历史订单不为空，依次查询订单详情
            for (Orders orders : page) {
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查看订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO getOrderDetail(Long id) {
        OrderVO orderVO = new OrderVO();
        //查询订单的详细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        Orders orders = orderMapper.getByOrderId(id);
        BeanUtils.copyProperties(orders, orderVO);
        return orderVO;
    }

    /**
     * 取消订单
     *
     * @param id
     */
    @Override
    public void cancelOrder(Long id) throws Exception {
        //需要对订单的状态进行修改
        //但需要进行判断，只有在待支付和待接单的状态下才能直接取消订单
        //已接单的状态下，用户取消订单需要用电话来沟通商家
        //派送中状态下，用户取消订单需要用电话来沟通商家
        //在待接单状态下取消订单，需要给用户退款
        //取消订单后要修改订单状态为"已取消"
        Orders orders = orderMapper.getByOrderId(id);
        //订单不存在
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //除了待支付和待接单状态，其他状态不允许直接取消订单
        if (orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //订单在待接单状态下取消订单要进行退款
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //个人小程序无法进行微信支付
//            weChatPayUtil.refund(
//                    orders.getNumber(),
//                    orders.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            //设置支付状态为已退款
            orders.setPayStatus(Orders.REFUND);
        }

        //设置订单为已取消状态
        orders.setStatus(Orders.CANCELLED);
        //设置取消原因
        orders.setCancelReason("用户取消");
        //取消订单时间
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Transactional
    @Override
    public void repetition(Long id) throws Exception {
        //再来一单的逻辑是把之前下单之后该单的商品全部加入到购物车里！！而不是直接下单！！
        //查询之前下过的订单
        Long userId = BaseContext.getCurrentId();   //购物车中数据需要绑定当前操作用户id！
        Orders orders = orderMapper.getByOrderId(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //可能出问题找不到订单了
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
//        List<ShoppingCart> shoppingCarts=new ArrayList<>();
//        orderDetailList.forEach(orderDetail->{
//            ShoppingCart shoppingCart=new ShoppingCart();
//            BeanUtils.copyProperties(orderDetail,shoppingCart,"id");
//            shoppingCart.setUserId(userId);
//            shoppingCart.setCreateTime(LocalDateTime.now());
//            shoppingCarts.add(shoppingCart);
//        });
        // 答案的处理方式
        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCarts = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            //传入三个参数，第三个参数是第二个对象不需要拷贝的第一个对象的某个属性
            //显然订单详情表的主键id不能拷贝到购物车记录中！否则可能插入失败
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());


        shoppingCartMapper.insertBatch(shoppingCarts);

    }

    /**
     * 搜索订单
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        //复用分页查询接口
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderVoList = getOrderVoList(page);

        return new PageResult(page.getTotal(), orderVoList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //查询表，使用聚合函数统计各个状态的订单数量
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        //封装到VO中
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) throws Exception {
        //技巧：可以不查询该订单，直接设置传过来的id，然后再设置status就可以进行对应更新了
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //拒单需要说明理由,并且只有在"待接单"的情况才能拒单
        Orders ordersDB = orderMapper.getByOrderId(ordersRejectionDTO.getId());
        //如果订单状态不为待接单或者订单不存在
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款
            //个人小程序无法进行微信支付
//            String refund =weChatPayUtil.refund(
//                    orders.getNumber(),
//                    orders.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
        }

        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .payStatus(Orders.REFUND)
                .build();

//            //设置支付状态为已退款


        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        //取消订单后，如果用户给钱了要退款
        Orders ordersDB = orderMapper.getByOrderId(ordersCancelDTO.getId());
        //订单不存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //已取消的订单不能再取消
        if (ordersDB.equals(Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //获取支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款
            //个人小程序无法进行微信支付
//            String refund =weChatPayUtil.refund(
//                    orders.getNumber(),
//                    orders.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
        }
        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) throws Exception {
        Orders orderDB = orderMapper.getByOrderId(id);
        //如果订单不存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //如果不是待派送状态
        if (!orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //设置订单状态为派送中
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) throws Exception {
        //只有派送中的订单能设置状态为已完成
        Orders orderDB = orderMapper.getByOrderId(id);
        //如果订单不存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //如果订单状态不为派送中
        if (!orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //更新已完成状态和送达时间
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 客户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        //查看订单是否存在
        Orders orderDB = orderMapper.getByOrderId(id);

        //如果订单不存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map map = new HashMap();
        map.put("type","2");    //1来单提醒 2.用户催单
        map.put("orderId", id);
        map.put("content","订单号: "+orderDB.getNumber()+"客户催单");
        String json = JSON.toJSONString(map);
        //通过websocket向管理端发送信息
        webSocketServer.sendToAllClient(json);
    }


    //把page对象转换为vo对象，并在其中填入dishes属性
    private List<OrderVO> getOrderVoList(Page<Orders> page) {
        List<OrderVO> orderVOList = new ArrayList<>();
        List<Orders> ordersList = page.getResult();
        for (Orders orders : ordersList) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            //需要设置订单的菜品信息，需要查询订单详情表得到菜品信息
            String orderDishes = getOrderDishesStr(orders);

            //将订单菜品信息封装到orderVO中，并添加到集合里
            orderVO.setOrderDishes(orderDishes);
            orderVOList.add(orderVO);
        }
        return orderVOList;
    }

    private String getOrderDishesStr(Orders orders) {
        //查询订单菜品的详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3;）
        List<String> orderDishesList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        //将该订单所有的菜品信息拼接在一起
        return String.join("", orderDishesList);
    }

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;


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
        log.info("店铺的经纬度坐标: {}", shopCoordinate);
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
        log.info("用户收货地址的经纬度坐标: {}", userCoordinate);
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
        log.info("路线规划json: {}", json);
        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");
        log.info("店家与用户收货地址的距离: {}", distance);
        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }


}
