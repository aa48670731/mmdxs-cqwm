package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
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

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {
    //订单表查询用
    @Autowired
    private OrderMapper orderMapper;
    //用户表查询用
    @Autowired
    private UserMapper userMapper;
    //excel报表查询数据用
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 统计指定时间区间内的营业额
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //构建日期列表
        List<LocalDate> dateList = new ArrayList<>();
        //构造营业额列表
        List<Double> turnoverList = new ArrayList<>();
        while (!begin.equals(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        //把最后一天加上
        dateList.add(end);
        for (LocalDate date : dateList) {
            //计算当天的时间（orders里是datetime类型）
            //要想用localdatetime表示这一天，可以用大于等于这一天的开始时间00:00:00和小于等于这一天的结束时间23:59:59来表示
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //使用map封装查询条件，后面的数据统计一样可以通过map来重用该查询接口
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            //如果这一天的营业额为空，查不到，sum聚合函数返回就为null，显然是不行的，需要进行特判
            Double turnover = orderMapper.sumByMap(map);
            turnover = (turnover == null) ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 统计指定时间区间内的用户数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //构造日期列表
        List<LocalDate> dateList = new ArrayList<>();
        //构造新用户和总用户列表
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        while (!begin.equals(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        //把最后一天加上
        dateList.add(end);
        for (LocalDate date : dateList) {
            //计算当天的时间（orders里是datetime类型）
            //要想用localdatetime表示这一天，可以用大于等于这一天的开始时间00:00:00和小于等于这一天的结束时间23:59:59来表示
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //使用map封装查询条件，后面的数据统计一样可以通过map来重用该查询接口
            Map map = new HashMap();
            map.put("end", endTime);
            //统计迄今为止的总用户数
            Integer totalUser = userMapper.countByMap(map);
            map.put("begin", beginTime);
            //统计今天新增的用户数
            Integer newUser = userMapper.countByMap(map);
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 统计指定时间区间内的订单数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //构建日期列表
        List<LocalDate> dateList = new ArrayList<>();
        //构建订单、有效订单总数表
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();


        while (!begin.equals(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        //把最后一天加上
        dateList.add(end);
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //使用一种优雅的方式来获取订单数的代码，单独封装在一个私有方法里
            Integer order = getOrderCount(beginTime, endTime, null);
            Integer validOrder = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            //直接累加不够优雅和高效
//            totalOrderCount += order;
//            validOrderCount += validOrder;
            orderCountList.add(order);
            validOrderCountList.add(validOrder);
        }

        //使用更优雅的方式来进行订单、有效订单数的累加
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //有效订单完成率
        Double orderCompletionRate = 0.0;
        //涉及除法，要注意小心除数为0！！
        if(totalOrderCount!=0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .validOrderCount(validOrderCount)
                .totalOrderCount(totalOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 根据条件统计订单数量
     *
     * @param begin
     * @param end
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);
        return orderMapper.countByMap(map);
    }


    /**
     统计指定时间区间内的销量排名前10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        //构造销量dto类集合来接收mapper传过来的两份数据，菜品/套餐名和对应销量,且数据已经在sql语句中排好序了
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalseTop10(beginTime,endTime);
        //把数据分别取出来构造成字符串
        //下面是自己写的for循环，不够优雅
//        List<String> nameList = new ArrayList<>();
//        List<Integer> numberList=new ArrayList<>();
//        for(GoodsSalesDTO goodsSalesDTO:goodsSalesDTOList){
//            nameList.add(goodsSalesDTO.getName());
//            numberList.add(goodsSalesDTO.getNumber());
//        }
        //优雅的写法，依旧使用steam流
        List<String> nameList = goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        //返回vo数据
        return SalesTop10ReportVO
                .builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList,","))
                .build();
    }


    /**
     * 导出Excel运营数据报表
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response)  {
        //1.查询数据库，获取营业数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        LocalDateTime beginTime = LocalDateTime.of(dateBegin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(dateEnd, LocalTime.MAX);
        //查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(beginTime, endTime);
        //2.通过POI将数据写入到Excel文件中
        //这里使用反射，获取类加载器，然后再把项目源目录对应的文件路径传入
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            //基于模板文件创建一个新的excel文件
            XSSFWorkbook excel = new XSSFWorkbook(is);
            //填充数据-时间
            XSSFSheet sheet = excel.getSheet("Sheet1");
            sheet.getRow(1).getCell(1).setCellValue("时间:"+dateBegin+"至"+dateEnd);

            //POI概览数据写入
            //获得第4行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());
            //获得第5行
            row=sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //POI明细数据写入
            //明确了遍历30天
            for (int i = 0; i < 30; i++) {
                LocalDate date = dateBegin.plusDays(i); //第一天：i=0，加上了0相当于没变
                LocalDateTime begin = LocalDateTime.of(date, LocalTime.MIN);
                LocalDateTime end = LocalDateTime.of(date, LocalTime.MAX);
                BusinessDataVO businessData = workspaceService.getBusinessData(begin, end);
                row=sheet.getRow(i+7);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessDataVO.getTurnover());
                row.getCell(3).setCellValue(businessDataVO.getValidOrderCount());
                row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessDataVO.getUnitPrice());
                row.getCell(6).setCellValue(businessDataVO.getNewUsers());


            }
            

            //3.通过输出流将Excel文件下载到客户端浏览器中
            //获取到输出流对象，通过输出流把excel文件传到客户端
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            //释放资源
            is.close();
            out.close();
            excel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
