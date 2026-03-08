package com.sky.task;


import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Slf4j
@Component
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时（超过15分钟）订单的方法
     */
    @Scheduled(cron = "0 0/1 * * * ? ")
//    @Scheduled(cron = "1/5 * * * * ?")  //测试用
    public void processTimeoutOrder(){
        log.info("定时处理超时订单: {},", LocalDateTime.now());

        //计算当前时间减去15分钟
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        //查出来结果不为空
        if(ordersList!=null && ordersList.size()>0){
            for (Orders orders : ordersList) {
                //设置取消
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());
                //记得更新数据库
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理一直处于派送中状态的订单，每到凌晨1点都会执行一次这个方法
     */
    @Scheduled(cron = "0 0 1 * * ?")
//    @Scheduled(cron = "0/5 * * * * ?")  //测试用
    public void processDeliveryOrder() {
        log.info("定时处理处于派送中的订单: {}", LocalDateTime.now());

        //一般估计送达时间不超1小时，加上一小时还小于凌晨1点的订单直接设置为已完成
        LocalDateTime time = LocalDateTime.now().plusHours(-1);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        //查出来结果不为空
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                //设置完成
                orders.setStatus(Orders.COMPLETED);
                //送达时间统一为凌晨1点
                orders.setDeliveryTime(LocalDateTime.now());
                //记得更新数据库
                orderMapper.update(orders);
            }
        }


    }

}
