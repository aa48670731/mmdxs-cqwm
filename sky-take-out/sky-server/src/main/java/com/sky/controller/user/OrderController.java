package com.sky.controller.user;


import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/user/order")
@RestController("userOrderController")
@Api(tags = "C端-订单相关接口")
public class OrderController {


    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单，参数为: {}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }


    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }


    /**
     * 查询历史订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     * @throws Exception
     */
    @GetMapping("/historyOrders")
    @ApiOperation("查询历史订单") //由于orderspagequerydto有很多属性用不上，这里传参只用传3个值
    public Result<PageResult> history(Integer page,Integer pageSize,Integer status) throws Exception {
        log.info("查询历史订单: {},{},{}", page, pageSize, status );
        PageResult pageResult = orderService.history(page,pageSize,status);
        return Result.success(pageResult);
    }

    /**
     * 查看订单详情
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查看订单详情")
    public Result<OrderVO> getOrderDetail(@PathVariable("id") Long id){
        log.info("查看订单详情: {}",id);
        OrderVO orderVO=orderService.getOrderDetail(id);
        return Result.success(orderVO);
    }


    /**
     * 取消订单
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancelOrder(@PathVariable("id") Long id) throws Exception {
        log.info("取消订单：{}",id);
        orderService.cancelOrder(id);
        return Result.success();
    }

    /**
     * 再来一单
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable("id") Long id) throws Exception {
        log.info("再来一单: {}",id);
        orderService.repetition(id);
        return Result.success();
    }


    /**
     * 用户催单
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("用户催单")
    public Result reminder(@PathVariable("id") Long id) {
        orderService.reminder(id);
        return Result.success();
    }


}
