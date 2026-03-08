package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

//命名bean对象，和管理端的controller区分开
@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {

    //redis数据库操作对象
    @Autowired
    private RedisTemplate redisTemplate;

    //使用的service之前已经写好了，直接复用
    @Autowired
    private DishService dishService;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        //构造约定好的key，准备用来查询redis数据库，规则：dish_分类id
        String key="dish_"+categoryId;
        //查询redis中是否已经存在菜品数据
        ValueOperations valueOperations = redisTemplate.opsForValue();
        List<DishVO> list = (List<DishVO>)valueOperations.get(key);
        //如果存在则直接返回，无需查询mysql数据库
        if(list!=null){
            return Result.success(list);
        }

        //若不存在，则查询数据库，并且把查询到的数据写入redis中
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        list = dishService.listWithFlavor(dish);
        //将分类下菜品全部缓存到redis中
        valueOperations.set(key, list);

        return Result.success(list);
    }

}
